/*
 * AniBeat — Android WebView bridge (no-op on the regular website).
 *
 * This file is loaded BEFORE the application bundle. When the page runs inside
 * the AniBeat Android app the native layer injects `window.AniBeatNative`
 * (see WebAppBridge.java). In that case this shim transparently fills the small
 * number of browser APIs that Android WebView does not implement, so that the
 * web app keeps behaving *exactly* like it does in Chrome on Android:
 *
 *   • navigator.mediaSession   -> native MediaSession (lockscreen / notification)
 *   • MediaMetadata            -> native metadata push
 *   • navigator.share          -> native share sheet
 *   • <a download href="blob:"> -> native file save (MediaStore/Downloads)
 *   • Android hardware back    -> history back (handled natively)
 *
 * On a normal browser `window.AniBeatNative` is undefined and this script
 * returns immediately: the website is byte-for-byte the same app.
 */
(function () {
  var B = window.AniBeatNative;
  if (!B) return;

  var bridge = B; // injected Java object
  var DEBUG = false;
  function log(msg) {
    if (DEBUG) {
      try {
        bridge.log(String(msg));
      } catch (e) {}
    }
  }

  /* ------------------------------------------------------------------ */
  /* Media Session (lockscreen / notification controls)                  */
  /* ------------------------------------------------------------------ */
  var handlers = {};
  var metadata = null;

  function artworkJson(md) {
    if (!md || !md.artwork) return [];
    var out = [];
    var list = md.artwork;
    for (var i = 0; i < list.length; i++) {
      var a = list[i];
      if (!a || !a.src) continue;
      out.push({ src: String(a.src), sizes: a.sizes ? String(a.sizes) : "", type: a.type ? String(a.type) : "" });
    }
    return out;
  }

  function pushMetadata(md) {
    try {
      bridge.updateMetadata(
        JSON.stringify({
          title: md && md.title ? String(md.title) : "",
          artist: md && md.artist ? String(md.artist) : "",
          album: md && md.album ? String(md.album) : "",
          artwork: artworkJson(md),
        })
      );
    } catch (e) {}
  }

  function pushPlaybackState(playing, position, duration, rate) {
    try {
      bridge.updatePlaybackState(
        JSON.stringify({
          playing: !!playing,
          position: typeof position === "number" && isFinite(position) ? position : 0,
          duration: typeof duration === "number" && isFinite(duration) ? duration : 0,
          rate: typeof rate === "number" && isFinite(rate) ? rate : 1,
        })
      );
    } catch (e) {}
  }

  var playbackState = "none";

  var mediaSession = {
    setActionHandler: function (action, handler) {
      if (handler) handlers[action] = handler;
      else delete handlers[action];
    },
    setPositionState: function (state) {
      if (!state) return;
      lastPosition = state.position || 0;
      lastDuration = state.duration || 0;
      pushPlaybackState(playbackState === "playing", lastPosition, lastDuration, state.playbackRate || 1);
    },
    setMicrophoneActive: function () {},
    setCameraActive: function () {},
  };
  var lastPosition = 0;
  var lastDuration = 0;

  Object.defineProperty(mediaSession, "metadata", {
    get: function () {
      return metadata;
    },
    set: function (v) {
      metadata = v;
      pushMetadata(v);
    },
    configurable: true,
  });
  Object.defineProperty(mediaSession, "playbackState", {
    get: function () {
      return playbackState;
    },
    set: function (v) {
      playbackState = v || "none";
      if (playbackState !== "none") pushPlaybackState(playbackState === "playing", lastPosition, lastDuration, 1);
    },
    configurable: true,
  });

  try {
    Object.defineProperty(navigator, "mediaSession", { value: mediaSession, configurable: true, writable: false });
  } catch (e) {
    try {
      navigator.mediaSession = mediaSession;
    } catch (e2) {}
  }

  if (typeof window.MediaMetadata === "undefined") {
    window.MediaMetadata = function MediaMetadata(init) {
      var o = init || {};
      this.title = o.title || "";
      this.artist = o.artist || "";
      this.album = o.album || "";
      this.artwork = o.artwork || [];
    };
  }

  /* Native -> JS media commands */
  function onMediaAction(action, detail) {
    var h = handlers[action];
    if (!h) return;
    try {
      if (action === "seekto") h({ seekTime: detail, fastSeek: false });
      else if (action === "seekbackward") h({ seekOffset: detail || 10 });
      else if (action === "seekforward") h({ seekOffset: detail || 10 });
      else h(detail || {});
    } catch (e) {
      log("media action failed: " + action + " " + e);
    }
  }

  /* ------------------------------------------------------------------ */
  /* navigator.share -> native share sheet                               */
  /* ------------------------------------------------------------------ */
  var nativeShare = function (data) {
    data = data || {};
    return new Promise(function (resolve, reject) {
      try {
        var ok = bridge.share(String(data.title || ""), String(data.text || ""), String(data.url || ""));
        if (ok === "cancel") reject(new DOMException("Share cancelled", "AbortError"));
        else resolve();
      } catch (e) {
        reject(e);
      }
    });
  };
  try {
    Object.defineProperty(navigator, "share", { value: nativeShare, configurable: true });
    Object.defineProperty(navigator, "canShare", {
      value: function () {
        return true;
      },
      configurable: true,
    });
  } catch (e) {}

  /* ------------------------------------------------------------------ */
  /* <a download href="blob:..."> -> native file save                    */
  /* ------------------------------------------------------------------ */
  var CHUNK = 256 * 1024;

  function bytesToBase64(bytes) {
    var s = "";
    var step = 8192;
    for (var i = 0; i < bytes.length; i += step) {
      s += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + step, bytes.length)));
    }
    return btoa(s);
  }

  var saving = {};
  function saveBlobNative(blobUrl, filename) {
    if (saving[blobUrl]) return;
    saving[blobUrl] = true;
    fetch(blobUrl)
      .then(function (r) {
        return r.blob();
      })
      .then(function (blob) {
        var name = filename || "anibeat-file";
        var type = blob.type || "application/octet-stream";
        var total = blob.size;
        var started = bridge.beginFile(name, type, total);
        if (String(started).indexOf("error") === 0) {
          throw new Error(started);
        }
        var offset = 0;
        function next() {
          if (offset >= total) {
            var done = bridge.endFile();
            if (String(done).indexOf("error") === 0) throw new Error(done);
            saving[blobUrl] = false;
            return;
          }
          var end = Math.min(offset + CHUNK, total);
          var slice = blob.slice(offset, end);
          return slice
            .arrayBuffer()
            .then(function (buf) {
              var res = bridge.writeChunk(bytesToBase64(new Uint8Array(buf)));
              if (String(res).indexOf("error") === 0) throw new Error(res);
              offset = end;
              bridge.fileProgress(offset, total);
              return next();
            });
        }
        return next();
      })
      .catch(function (e) {
        saving[blobUrl] = false;
        log("save failed: " + e);
        try {
          bridge.toast("Не удалось сохранить файл");
        } catch (e2) {}
      });
  }

  document.addEventListener(
    "click",
    function (e) {
      var el = e.target;
      while (el && el !== document && !(el.tagName === "A" && el.hasAttribute && el.hasAttribute("download"))) {
        el = el.parentNode;
      }
      if (!el || el === document) return;
      var href = el.href || "";
      if (href.indexOf("blob:") !== 0) return;
      e.preventDefault();
      e.stopPropagation();
      saveBlobNative(href, el.getAttribute("download"));
    },
    true
  );

  /* ------------------------------------------------------------------ */
  /* Native -> JS events                                                 */
  /* ------------------------------------------------------------------ */
  window.__AniBeatNativeEvent = function (name, payloadJson) {
    var payload = {};
    try {
      payload = payloadJson ? JSON.parse(payloadJson) : {};
    } catch (e) {}
    if (name === "mediaAction") onMediaAction(payload.action, payload.value);
    else if (name === "pause") {
      /* audio focus lost / task removed */
      var t = document.querySelector("video,audio");
      if (t && !t.paused) {
        var h = handlers["pause"];
        if (h) {
          try {
            h({});
          } catch (e) {}
        }
      }
    } else if (name === "back") {
      window.dispatchEvent(new Event("anibeat:back"));
    }
  };

  /* Let the native layer know we are ready (needed to restore a session). */
  try {
    bridge.onReady();
  } catch (e) {}
})();
