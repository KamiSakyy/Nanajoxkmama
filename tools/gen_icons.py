#!/usr/bin/env python3
"""AniBeat icon set — Material Design geometry (24x24), pure black theme.

Every path follows Google Material Icons proportions on a 24x24 grid:
2dp keylines, 2dp live area padding, consistent optical weight.
Run: python3 tools/gen_icons.py
"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

# Official-style Material path data (24x24). Filled icons use fill; outlined use stroke on the
# exact outline geometry (matching Material Symbols Outlined look).
FILL = "fill"
STROKE = "stroke"

ICONS = {
    # ---------- playback ----------
    "play_arrow": (FILL, "M8,5 L8,19 L19,12 Z"),
    "pause": (FILL, "M6,19 L10,19 L10,5 L6,5 Z M14,19 L18,19 L18,5 L14,5 Z"),
    "skip_next": (FILL, "M6,18 L6,6 L15,12 Z M16,6 L18,6 L18,18 L16,18 Z"),
    "skip_previous": (FILL, "M8,6 L6,6 L6,18 L8,18 Z M18,6 L9,12 L18,18 Z"),
    "replay_10": (STROKE, "M4.6 9.2 A8 8 0 1 1 4 12 M2.2 5.5 L4.6 9.2 L8.7 7.5 M9.2 10.5 L8.3 14.6 L10.5 10.8 M12.3 10.5 L11.4 14.6 L13.6 10.8 M15.2 12.2 C15.2 12.2 16 10.6 17.5 10.8 C18.8 10.9 18.8 12.4 17.5 12.5 C16.4 12.6 15.2 14.5 15.2 14.5"),
    "forward_10": (STROKE, "M19.4 9.2 A8 8 0 1 0 20 12 M21.8 5.5 L19.4 9.2 L15.3 7.5 M8.8 10.5 L7.9 14.6 L10.1 10.8 M11.7 10.5 L10.8 14.6 L13 10.8 M15.2 12.2 C15.2 12.2 16 10.6 17.5 10.8 C18.8 10.9 18.8 12.4 17.5 12.5 C16.4 12.6 15.2 14.5 15.2 14.5"),
    "shuffle": (STROKE, "M3 6h3.5c1.4 0 2.7.7 3.5 1.8l4 6.3c.8 1.2 2.1 1.9 3.5 1.9H21 M3 18h3.5c1.4 0 2.7-.7 3.5-1.8 M14.5 7.8c.8-1.1 2-1.8 3.5-1.8H21 M18.3 3.8 21 6l-2.7 2.2 M18.3 15.8 21 18l-2.7 2.2"),
    "repeat": (STROKE, "M7 4h10a4 4 0 0 1 4 4v2 M17 20H7a4 4 0 0 1-4-4v-2 M15 2l2.5 2.2L15 6.4 M9 22l-2.5-2.2L9 17.6"),
    "repeat_one": (STROKE, "M7 4h10a4 4 0 0 1 4 4v2 M17 20H7a4 4 0 0 1-4-4v-2 M15 2l2.5 2.2L15 6.4 M9 22l-2.5-2.2L9 17.6 M12 9.2 L10.8 10.2 L10.8 15"),
    "stop": (FILL, "M6,6 L18,6 L18,18 L6,18 Z"),
    "speed": (STROKE, "M12 4a9 9 0 0 1 9 9 9 9 0 0 1-.6 3.2 M12 4a9 9 0 0 0-9 9c0 1.5.4 2.9 1 4.2 M3.5 14h3l2-4 3 7 2.5-6 1.5 3h4.5 M4.5 19.5 A9 9 0 0 0 12 22a9 9 0 0 0 8.5-6"),
    # ---------- actions ----------
    "search": (STROKE, "M10.5 3.5a7 7 0 1 1 0 14 7 7 0 0 1 0-14 M20.5 20.5l-5.2-5.2"),
    "close": (STROKE, "M6 6l12 12 M18 6L6 18"),
    "add": (STROKE, "M12 5v14 M5 12h14"),
    "check": (STROKE, "M4.5 12.5l5 5L19.5 7"),
    "check_circle": (FILL, "M12,2 A10,10 0 1,0 22,12 10,10 0 0,0 12,2 M10.6,15.6 6,11 L7.4,9.6 10.6,12.8 16.6,6.8 18,8.2 Z"),
    "delete": (FILL, "M6,19 C6,20.1 6.9,21 8,21 L16,21 C17.1,21 18,20.1 18,19 L18,7 L6,7 Z M19,4 L15.5,4 L14.5,3 L9.5,3 L8.5,4 L5,4 L5,6 L19,6 Z"),
    "edit": (STROKE, "M4 20l1-4.2L15.8 5c.4-.4 1-.4 1.4 0l2.8 2.8c.4.4.4 1 0 1.4L9.2 20 4 20z M14.6 6.4l3 3"),
    "share": (STROKE, "M15 8a3 3 0 1 0-2.8-4H12a3 3 0 0 0 .3 1.4L8.2 8.2A3 3 0 0 0 6.8 7.5L6.6 7.6A3 3 0 1 0 8 10.5l4.2-2.6A3 3 0 0 0 15 8z M8 15.5a3 3 0 1 0-.2 3.8 M15.5 14a3 3 0 1 0 .2 3.8 M8.5 17.4l3.6 2.2M15.2 15.8l-3.5 2"),
    "download": (STROKE, "M12 3v11 M7.5 10.5L12 15l4.5-4.5 M4 19h16"),
    "download_done": (STROKE, "M4 12.5l5 5L20 6.5 M4 20h16"),
    "cloud_download": (STROKE, "M7 17.5a4.5 4.5 0 0 1-.4-9 6 6 0 0 1 11.6 1.6A3.9 3.9 0 0 1 17.5 17.5 M12 10v8 M8.8 14.8L12 18l3.2-3.2"),
    "offline_pin": (FILL, "M12,2 A10,10 0 1,0 22,12 10,10 0 0,0 12,2 M8.4,12.2 11,14.8 15.8,10 14.4,8.6 11,12 9.8,10.8 Z"),
    "queue_music": (STROKE, "M3 5h12v2H3z M3 10h12v2H3z M3 15h8v2H3z M19 6v9.2a2.8 2.8 0 1 1-2-2.6V8h4V6h-2z"),
    "playlist_add": (STROKE, "M3 5h12v2H3z M3 10h12v2H3z M3 15h8v2H3z M16 14v6 M13 17h6"),
    "playlist_play": (STROKE, "M3 5h12v2H3z M3 10h12v2H3z M3 15h7v2H3z M14 12.5v7l6-3.5z"),
    "favorite": (FILL, "M12,21 L10.6,19.7 C5.4,15 2,11.9 2,8.1 2,5 4.4,2.6 7.5,2.6 c1.7,0 3.4,.8 4.5,2.1 C13.1,3.4 14.8,2.6 16.5,2.6 19.6,2.6 22,5 22,8.1 c0,3.8 -3.4,6.9 -8.6,11.6 z"),
    "favorite_border": (STROKE, "M12 20.3l-1.1-1C6.1 15 3 12.2 3 8.8 3 6 5.2 3.8 8 3.8c1.6 0 3.1.7 4.1 1.9 1-1.2 2.5-1.9 4.1-1.9 2.8 0 5 2.2 5 5 0 3.4-3.1 6.2-7.9 10.5l-1.3 1z"),
    "videocam": (STROKE, "M3.5 6.5h11a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z M15.5 10.2L21 7v10l-5.5-3.2"),
    "videocam_off": (STROKE, "M3.5 6.5h9.6 M15.5 8.6V7.5a1 1 0 0 0-1-1h-8.4 M3.5 6.5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-3.4 M3 3l18 18 M21 7v10l-3.7-2.2"),
    "radio": (STROKE, "M4.5 9.5h13.2a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H6.3a2 2 0 0 1-2-2v-6.5a2 2 0 0 1 .2-.5z M4.8 9.2l9.4-5.4 M7 13.8a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 0 0-3.6 0 M14 13h4 M14 16h4"),
    "mic": (STROKE, "M12 3.5a2.8 2.8 0 0 1 2.8 2.8v5.4a2.8 2.8 0 1 1-5.6 0V6.3A2.8 2.8 0 0 1 12 3.5 M6 11a6 6 0 0 0 12 0 M12 17v3.5 M8.5 20.5h7"),
    "storage": (STROKE, "M4 5.5h16v5H4z M4 13.5h16v5H4z M7 8h.01 M7 16h.01"),
    "cached": (STROKE, "M20 12a8 8 0 1 1-2.3-5.6 M20 3v4h-4"),
    "refresh": (STROKE, "M20 12a8 8 0 1 1-2.3-5.6 M20 3v4h-4 M12 8v4l3 2"),
    "filter_alt": (FILL, "M4,5 L20,5 L14,12.2 L14,19 L10,17 L10,12.2 Z"),
    "clear_all": (STROKE, "M4 6h16 M6 11h12 M9 16h6"),
    # ---------- nav ----------
    "home": (FILL, "M12,3 L2,12 L5,12 L5,21 L10,21 L10,15 L14,15 L14,21 L19,21 L19,12 L22,12 Z"),
    "home_outline": (STROKE, "M4 10.2L12 3.5l8 6.7v9.3a1 1 0 0 1-1 1h-4.6v-6h-4.8v6H5a1 1 0 0 1-1-1z"),
    "explore": (FILL, "M12,2 A10,10 0 1,0 22,12 10,10 0 0,0 12,2 M15.6,8.4 L13.4,13.4 L8.4,15.6 L10.6,10.6 Z"),
    "explore_outline": (STROKE, "M12 2.7a9.3 9.3 0 1 1 0 18.6 9.3 9.3 0 0 1 0-18.6 M15 9l-3.4 2.6L9 15l3.4-2.6z"),
    "library_music": (FILL, "M4,3 L4,21 L2,21 L2,3 Z M7,3 L7,21 L5,21 L5,3 Z M20,3 L8,5 L8,18 L20,16 Z M17.5,7.2 L14.5,7.7 L14.5,14.1 c-1,-.4 -2.1,-.2 -2.9,.5 c-1,.8 -1.2,2.3 -.3,3.3 c.9,1 2.6,1 3.7,-.2 c.6,-.7 .8,-1.5 .7,-2.3 L15.7,9.4 L18.5,9 L18.5,11.2 L17.5,11.4 Z"),
    "library_music_outline": (STROKE, "M3 3h2v18H3z M7 3h2v18H7z M10 5.2L20 3.5v13L10 15.2z M15.2 7.4v6.3a2 2 0 1 1-1.2-1.8V8.4l2.6-.5"),
    "music_note": (FILL, "M20,3 L9,5.5 L9,17.3 c-.9,-.5 -2,-.6 -3,-.2 C4,17.9 3,19.8 3.4,21.5 c.4,1.7 2.3,2.7 4.2,2.2 c1.6,-.4 2.6,-1.9 2.4,-3.4 L20,18.7 Z"),
    "queue": (STROKE, "M3 5h12v2H3z M3 10h12v2H3z M3 15h8v2H3z M16 10h5v2h-5z M16 15h5v2h-5z"),
    "person": (STROKE, "M12 3.8a3.8 3.8 0 1 1 0 7.6 3.8 3.8 0 0 1 0-7.6 M4.5 20.5a7.5 7.5 0 0 1 15 0"),
    "mic_external": (STROKE, "M8 4h8v9H8z M12 13v5 M8 21h8 M5 8H3v2a4 4 0 0 0 3 3.9 M19 8h2v2a4 4 0 0 1-3 3.9"),
    "whatshot": (FILL, "M13.5,0.7 c.4,2.5 -.4,4.4 -1.8,5.8 C10.2,8 9,9.6 9,11.4 c0,1.1 .3,2.1 .8,2.9 C8.6,13.9 8,12.7 8,11.2 c-1.7,1.3 -2.7,3.3 -2.7,5.5 c0,4 3.3,7.3 7.3,7.3 c4,0 7.3,-3.3 7.3,-7.3 C20,8.7 16.8,3.9 13.5,.7 Z"),
    "trending_up": (STROKE, "M3 17l6-6 4 4 7-7 M15 8h5v5"),
    "bolt": (FILL, "M11,2 L5,13 L10,13 L9,22 L19,10 L13,10 Z"),
    "auto_awesome": (FILL, "M12,2 L13.6,8.4 L20,10 L13.6,11.6 L12,18 L10.4,11.6 L4,10 L10.4,8.4 Z M19,14 L19.8,16.2 L22,17 L19.8,17.8 L19,20 L18.2,17.8 L16,17 L18.2,16.2 Z"),
    "casino": (STROKE, "M4.5 4.5h15v15h-15z M8.3 8.3h.01 M12 8.3h.01 M15.7 8.3h.01 M8.3 12h.01 M12 12h.01 M15.7 12h.01 M8.3 15.7h.01 M12 15.7h.01 M15.7 15.7h.01"),
    "casino_filled": (FILL, "M5,4 L19,4 C19.6,4 20,4.4 20,5 L20,19 C20,19.6 19.6,20 19,20 L5,20 C4.4,20 4,19.6 4,19 L4,5 C4,4.4 4.4,4 5,4 M7.5,8 A1.2,1.2 0 1,0 7.5,10.4 1.2,1.2 0 0,0 7.5,8 M16.5,8 A1.2,1.2 0 1,0 16.5,10.4 1.2,1.2 0 0,0 16.5,8 M12,13.6 A1.2,1.2 0 1,0 12,16 1.2,1.2 0 0,0 12,13.6"),
    "layers": (STROKE, "M12 3.5L2.5 8 12 12.5 21.5 8z M4 12l8 4 8-4 M4 16l8 4 8-4"),
    "sensors": (STROKE, "M8.5 15.5a5 5 0 0 1 0-7 M15.5 8.5a5 5 0 0 1 0 7 M5.7 18.3a9 9 0 0 1 0-12.6 M18.3 5.7a9 9 0 0 1 0 12.6 M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2"),
    "data_saver": (STROKE, "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17 M12 8.5v3l2.5 2 M9 2.5l3 1 3-1"),
    "translate": (STROKE, "M3.5 5.5h9 M8 3.5V5.5 M10.8 5.5c-.7 4-3.3 7.2-7.3 9 M6 8.7c1 2.5 3.3 4.7 6.2 5.8 M11.5 12l4 9 M13.2 18.5h6.3 M20 15.5l-4 9"),
    "language": (STROKE, "M12 2.7a9.3 9.3 0 1 1 0 18.6 9.3 9.3 0 0 1 0-18.6 M2.7 12h18.6 M12 2.7c2.4 2.4 3.7 5.6 3.7 9.3s-1.3 6.9-3.7 9.3c-2.4-2.4-3.7-5.6-3.7-9.3S9.6 5.1 12 2.7z"),
    "tv": (STROKE, "M3.5 6.5h17a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-17a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1z M8 21h8 M12 3l-2.2 2.5 M12 3l2.2 2.5"),
    "graphic_eq": (STROKE, "M5 14v5 M9.5 9v10 M14 5v14 M18.5 11v8"),
    "star": (FILL, "M12,2.6 L14.8,8.8 L21.6,9.4 L16.5,14 17.9,20.8 12,17.3 6.1,20.8 7.5,14 2.4,9.4 9.2,8.8 Z"),
    "star_rate": (FILL, "M12,2.6 L14.8,8.8 L21.6,9.4 L16.5,14 17.9,20.8 12,17.3 6.1,20.8 7.5,14 2.4,9.4 9.2,8.8 Z"),
    "schedule": (STROKE, "M12 2.7a9.3 9.3 0 1 1 0 18.6 9.3 9.3 0 0 1 0-18.6 M12 6.8V12l3.6 2.4"),
    "today": (STROKE, "M4.5 5.5h15a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z M3.5 10h17 M8 3.5v4 M16 3.5v4 M12 13.5v3l2 1.4"),
    "calendar": (STROKE, "M4.5 5.5h15a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z M3.5 10h17 M8 3.5v4 M16 3.5v4"),
    # ---------- ui chrome ----------
    "arrow_back": (STROKE, "M20 12H4.5 M11 5.5L4.5 12l6.5 6.5"),
    "expand_more": (STROKE, "M6.5 9.5L12 15l5.5-5.5"),
    "expand_less": (STROKE, "M6.5 14.5L12 9l5.5 5.5"),
    "keyboard_arrow_up": (STROKE, "M6.5 14.5L12 9l5.5 5.5"),
    "keyboard_arrow_down": (STROKE, "M6.5 9.5L12 15l5.5-5.5"),
    "chevron_right": (STROKE, "M9.5 6.5L15 12l-5.5 5.5"),
    "more_horiz": (FILL, "M6,10.2 A1.8,1.8 0 1,0 6,13.8 1.8,1.8 0 0,0 6,10.2 M12,10.2 A1.8,1.8 0 1,0 12,13.8 1.8,1.8 0 0,0 12,10.2 M18,10.2 A1.8,1.8 0 1,0 18,13.8 1.8,1.8 0 0,0 18,10.2"),
    "more_vert": (FILL, "M10.2,6 A1.8,1.8 0 1,0 13.8,6 1.8,1.8 0 0,0 10.2,6 M10.2,12 A1.8,1.8 0 1,0 13.8,12 1.8,1.8 0 0,0 10.2,12 M10.2,18 A1.8,1.8 0 1,0 13.8,18 1.8,1.8 0 0,0 10.2,18"),
    "settings": (STROKE, "M12 9.2a2.8 2.8 0 1 1 0 5.6 2.8 2.8 0 0 1 0-5.6 M19.3 13.5l2 1.1-2 3.5-2.1-1a7.7 7.7 0 0 1-1.8 1.1l-.2 2.3h-4l-.2-2.3a7.7 7.7 0 0 1-1.8-1.1l-2.1 1-2-3.5 2-1.1a7.4 7.4 0 0 1 0-2.1l-2-1.1 2-3.5 2.1 1a7.7 7.7 0 0 1 1.8-1.1L11 4.5h4l.2 2.3c.6.3 1.2.6 1.8 1.1l2.1-1 2 3.5-2 1.1a7.4 7.4 0 0 1 .2 2.1z"),
    "info": (STROKE, "M12 2.7a9.3 9.3 0 1 1 0 18.6 9.3 9.3 0 0 1 0-18.6 M12 10.6v6 M12 7.2h.01"),
    "error_outline": (STROKE, "M12 2.7a9.3 9.3 0 1 1 0 18.6 9.3 9.3 0 0 1 0-18.6 M12 7.5v5.5 M12 16.2h.01"),
    "wifi_off": (STROKE, "M2.5 3l18 18 M8.2 12.4a6 6 0 0 1 3.3-1.6 M4.5 9.5a11 11 0 0 1 3.2-2.1 M16.4 8.2a11 11 0 0 1 3.1 1.3 M10.3 15.5a2.3 2.3 0 0 1 2.7-.4 M12 18.8h.01 M6.2 12.8c1-1 2.2-1.7 3.5-2.2"),
    "history": (STROKE, "M12 4.5a8 8 0 1 1-7.5 5.2 M2.5 8.8L4.5 12l3.3-1.6 M12 7.5V12l3.3 2.2"),
    "first_page": (STROKE, "M17.5 5.5L10 12l7.5 6.5 M7 5.5v13"),
    "last_page": (STROKE, "M6.5 5.5L14 12l-7.5 6.5 M17 5.5v13"),
    "open_in_new": (STROKE, "M13.5 4.5H19.5V10.5 M19.5 4.5L11 13 M17 14v4.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1h4.5"),
    "fullscreen": (STROKE, "M4 9V5a1 1 0 0 1 1-1h4 M15 4h4a1 1 0 0 1 1 1v4 M20 15v4a1 1 0 0 1-1 1h-4 M9 20H5a1 1 0 0 1-1-1v-4"),
    "fullscreen_exit": (STROKE, "M9 4v4a1 1 0 0 1-1 1H4 M20 9h-4a1 1 0 0 1-1-1V4 M15 20v-4a1 1 0 0 1 1-1h4 M4 15h4a1 1 0 0 1 1 1v4"),
    "volume_up": (STROKE, "M4 9.5h3.2L12 5.8v12.4L7.2 14.5H4z M15.5 9a4.5 4.5 0 0 1 0 6 M18 6.5a8 8 0 0 1 0 11"),
    "volume_down": (STROKE, "M4 9.5h3.2L12 5.8v12.4L7.2 14.5H4z M15.5 9a4.5 4.5 0 0 1 0 6"),
    "volume_off": (STROKE, "M4 9.5h3.2L12 5.8v12.4L7.2 14.5H4z M16 9.5l5 5 M21 9.5l-5 5"),
    "equalizer": (STROKE, "M5 20v-5 M5 11V4 M12 20v-9 M12 7V4 M19 20v-3 M19 13V4"),
    # ---------- media/state ----------
    "music_placeholder": (STROKE, "M9 18.2a2.8 2.8 0 1 1-2-2.7V6.5l10-2.2v9.8 M9 18.2V8.8l10-2.2"),
    "notification": (STROKE, "M6 9.5a6 6 0 0 1 12 0v4l1.8 3H4.2L6 13.5z M9.5 19.5a2.6 2.6 0 0 0 5 0"),
    "launcher": (STROKE, "M4.5 4.5h15a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z M8 15.5V9.8c0-1.5 1.2-2.8 2.7-2.8h.6c1.5 0 2.7 1.3 2.7 2.8v5.7 M8 13h6"),
}

HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_icons.py — Material geometry, AniBeat black theme. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
'''

def emit(name, kind, d):
    if kind == FILL:
        path = ('    <path\n'
                '        android:pathData="%s"\n'
                '        android:fillColor="#FFFFFFFF"/>' % d)
    else:
        path = ('    <path\n'
                '        android:pathData="%s"\n'
                '        android:fillColor="#00000000"\n'
                '        android:strokeColor="#FFFFFFFF"\n'
                '        android:strokeWidth="1.8"\n'
                '        android:strokeLineCap="round"\n'
                '        android:strokeLineJoin="round"/>' % d)
    xml = HEADER + path + '\n</vector>\n'
    with open(os.path.join(OUT, "ic_%s.xml" % name), "w") as f:
        f.write(xml)

def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (kind, d) in ICONS.items():
        emit(name, kind, d)
    print("wrote %d icons to %s" % (len(ICONS), os.path.abspath(OUT)))

if __name__ == "__main__":
    main()
