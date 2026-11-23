package io.github.iampopovich.tunnelslide.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import io.github.iampopovich.tunnelslide.TunnelSlideGame;

/** Browser entry point (TeaVM). The canvas fills the page. */
public class WebLauncher {
    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        // 0 = follow the browser window size.
        config.width = 0;
        config.height = 0;
        // The game targets GLES 2 / WebGL 1 shaders everywhere.
        config.useGL30 = false;
        // gdx-freetype-web is an Emscripten module that has to be on the page before the game
        // creates its first font.
        config.preloadListener = assetLoader -> assetLoader.loadScript("freetype.js");
        new WebApplication(new TunnelSlideGame(), config);
    }
}
