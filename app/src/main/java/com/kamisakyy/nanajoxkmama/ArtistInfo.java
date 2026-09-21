package com.kamisakyy.nanajoxkmama;

import java.util.ArrayList;

/** Artist card data + detail payload. */
public final class ArtistInfo {
    public int id = -1;
    public String name = "";
    public String slug = "";
    public String image = "";
    public String imageSmall = "";
    public String information = "";
    public final ArrayList<Track> tracks = new ArrayList<>();
}
