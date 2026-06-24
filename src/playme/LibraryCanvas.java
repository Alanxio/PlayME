package playme;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Canvas de navegacion de biblioteca estilo iPod.
 * Soporta:
 * - Menu principal: Todas, Artistas, Albums, Favoritas, Buscar
 * - Listas con scroll y posicion (ej: 154/400)
 * - Salto rapido por letras (mantener arriba/abajo)
 * - Busqueda T9
 */
public class LibraryCanvas extends Canvas {

    private MusicPlayerMIDlet midlet;
    private PlaylistManager playlist;
    private ServerClient client;
    private Settings settings;
    private T9Input t9;

    // Estados de la pantalla
    public static final int STATE_MENU = 0;
    public static final int STATE_ALL_SONGS = 1;
    public static final int STATE_ARTISTS = 2;
    public static final int STATE_ALBUMS = 3;
    public static final int STATE_ARTIST_SONGS = 4;
    public static final int STATE_ALBUM_SONGS = 5;
    public static final int STATE_SEARCH = 6;
    public static final int STATE_FAVORITES = 7;

    private int state = STATE_MENU;

    // Menu principal
    private static final String[] MENU_ITEMS = {
        "Todas", "Artistas", "Albumes", "Favoritas", "Buscar"
    };
    private int menuIndex = 0;

    // Lista de canciones
    private int listIndex = 0;
    private int listScroll = 0; // primera fila visible
    private int visibleRows = 6;

    // Lista de artistas/albumes
    private String[] categoryList;
    private int categoryIndex = 0;
    private int categoryScroll = 0;

    // Salto por letras
    private boolean letterJumpActive = false;
    private char currentLetter = 'A';
    private long letterJumpTime = 0;
    private static final long LETTER_JUMP_TIMEOUT = 1500;
    private long lastKeyRepeatTime = 0;
    private int keyRepeatCount = 0;

    // Fonts
    private Font fontSmall;
    private Font fontMedium;
    private Font fontLarge;

    // Colores (se sincronizan con PlayerCanvas)
    private int colorBgTop;
    private int colorBgBottom;
    private int colorGlass;
    private int colorText;
    private int colorTextShadow;
    private int colorAccent;
    private int colorSelected;
    private int colorSoftkey;

    // Carga asincrona
    private boolean loading = false;
    private String loadingMessage = "";

    public LibraryCanvas(MusicPlayerMIDlet midlet, PlaylistManager playlist,
                         ServerClient client, Settings settings) {
        this.midlet = midlet;
        this.playlist = playlist;
        this.client = client;
        this.settings = settings;
        this.t9 = new T9Input();

        setFullScreenMode(true);
        applyTheme();

        fontSmall = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontMedium = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontLarge = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);

        // Calcular filas visibles
        int itemH = fontSmall.getHeight() + 4;
        visibleRows = (getHeight() - 40) / itemH;
    }

    public void applyTheme() {
        switch (settings.theme) {
            case Settings.THEME_DARK:
                colorBgTop = 0x1A1A2E;
                colorBgBottom = 0x0F0F1A;
                colorGlass = 0x2A2A4A;
                colorText = 0xE0E0E0;
                colorTextShadow = 0x000000;
                colorAccent = 0x6C63FF;
                colorSelected = 0x3A3A5A;
                colorSoftkey = 0x8888AA;
                break;
            case Settings.THEME_NOKIA:
                colorBgTop = 0x003399;
                colorBgBottom = 0x001A66;
                colorGlass = 0x1A5CCC;
                colorText = 0xFFFFFF;
                colorTextShadow = 0x001133;
                colorAccent = 0x00AAFF;
                colorSelected = 0x0044AA;
                colorSoftkey = 0x88BBEE;
                break;
            default: // THEME_AERO
                colorBgTop = 0x0052A5;
                colorBgBottom = 0x003366;
                colorGlass = 0x3399DD;
                colorText = 0xFFFFFF;
                colorTextShadow = 0x002244;
                colorAccent = 0x44BBFF;
                colorSelected = 0x0066CC;
                colorSoftkey = 0xAADDFF;
                break;
        }
    }

    // --- Dibujo ---

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // Fondo degradado
        drawGradient(g, 0, 0, w, h, colorBgTop, colorBgBottom);

        // Glass effect header
        drawGlassEffect(g, 0, 0, w, 20);

        if (loading) {
            drawLoadingState(g, w, h);
            return;
        }

        // Salto por letra activo -> mostrar letra grande
        if (letterJumpActive) {
            long elapsed = System.currentTimeMillis() - letterJumpTime;
            if (elapsed < LETTER_JUMP_TIMEOUT) {
                drawLetterJump(g, w, h);
                return;
            } else {
                letterJumpActive = false;
            }
        }

        switch (state) {
            case STATE_MENU:
                drawMenu(g, w, h);
                break;
            case STATE_ALL_SONGS:
            case STATE_ARTIST_SONGS:
            case STATE_ALBUM_SONGS:
            case STATE_FAVORITES:
                drawSongList(g, w, h);
                break;
            case STATE_ARTISTS:
            case STATE_ALBUMS:
                drawCategoryList(g, w, h);
                break;
            case STATE_SEARCH:
                drawSearchScreen(g, w, h);
                break;
        }

        // Softkeys
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString("Atras", 2, h - 2, Graphics.BOTTOM | Graphics.LEFT);
        if (state == STATE_SEARCH) {
            g.drawString("Borrar", w - 2, h - 2, Graphics.BOTTOM | Graphics.RIGHT);
        } else if (state != STATE_MENU) {
            g.drawString("Reprod.", w - 2, h - 2, Graphics.BOTTOM | Graphics.RIGHT);
        }
    }

    /** Menu principal tipo iPod */
    private void drawMenu(Graphics g, int w, int h) {
        // Titulo
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Biblioteca", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Biblioteca", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        // Items del menu
        int startY = 24;
        int itemH = fontSmall.getHeight() + 8;

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int y = startY + i * itemH;

            if (i == menuIndex) {
                // Fondo de seleccion con efecto Aero
                g.setColor(colorSelected);
                g.fillRoundRect(4, y, w - 8, itemH - 2, 4, 4);
                // Brillo superior
                g.setColor(colorAccent);
                g.drawLine(6, y + 1, w - 6, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(i == menuIndex ? colorText : colorSoftkey);

            String label = MENU_ITEMS[i];
            // Anadir conteo
            if (i == 0 && playlist.isLoaded()) {
                label += " (" + playlist.getTotalSongs() + ")";
            }

            g.drawString(label, 12, y + 2, Graphics.TOP | Graphics.LEFT);

            // Flecha indicadora
            if (i == menuIndex) {
                g.setColor(colorAccent);
                g.drawString(">", w - 14, y + 2, Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    /** Lista de canciones */
    private void drawSongList(Graphics g, int w, int h) {
        int total = playlist.getTotalSongs();

        // Titulo
        g.setFont(fontMedium);
        String title = "Canciones";
        if (state == STATE_ARTIST_SONGS) title = playlist.getFilterValue();
        else if (state == STATE_ALBUM_SONGS) title = playlist.getFilterValue();
        else if (state == STATE_FAVORITES) title = "Favoritas";

        g.setColor(colorTextShadow);
        g.drawString(title, w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString(title, w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        // Contador
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString((listIndex + 1) + "/" + total, w - 4, 3, Graphics.TOP | Graphics.RIGHT);

        // Lista
        int startY = 22;
        int itemH = fontSmall.getHeight() + 4;

        for (int i = 0; i < visibleRows && (listScroll + i) < total; i++) {
            int idx = listScroll + i;
            int y = startY + i * itemH;

            if (idx == listIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(2, y, w - 4, itemH - 1, 3, 3);
                g.setColor(colorAccent);
                g.drawLine(4, y + 1, w - 4, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(idx == listIndex ? colorText : colorSoftkey);

            String songTitle = playlist.getTitleAt(idx);
            if (songTitle == null) songTitle = "...";
            songTitle = truncateText(songTitle, fontSmall, w - 16);

            g.drawString(songTitle, 8, y + 1, Graphics.TOP | Graphics.LEFT);
        }

        // Barra de scroll visual
        if (total > visibleRows) {
            int scrollBarH = h - 40;
            int thumbH = Math.max(8, scrollBarH * visibleRows / total);
            int thumbY = 22 + (scrollBarH - thumbH) * listScroll / (total - visibleRows);
            g.setColor(colorBarBg());
            g.fillRect(w - 3, 22, 2, scrollBarH);
            g.setColor(colorAccent);
            g.fillRect(w - 3, thumbY, 2, thumbH);
        }
    }

    /** Lista de categorias (artistas/albumes) */
    private void drawCategoryList(Graphics g, int w, int h) {
        if (categoryList == null) return;

        String title = (state == STATE_ARTISTS) ? "Artistas" : "Albumes";
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString(title, w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString(title, w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        // Contador
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString((categoryIndex + 1) + "/" + categoryList.length,
                     w - 4, 3, Graphics.TOP | Graphics.RIGHT);

        int startY = 22;
        int itemH = fontSmall.getHeight() + 4;

        for (int i = 0; i < visibleRows && (categoryScroll + i) < categoryList.length; i++) {
            int idx = categoryScroll + i;
            int y = startY + i * itemH;

            if (idx == categoryIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(2, y, w - 4, itemH - 1, 3, 3);
                g.setColor(colorAccent);
                g.drawLine(4, y + 1, w - 4, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(idx == categoryIndex ? colorText : colorSoftkey);

            String name = categoryList[idx];
            if (name == null) name = "...";
            name = truncateText(name, fontSmall, w - 16);
            g.drawString(name, 8, y + 1, Graphics.TOP | Graphics.LEFT);
        }
    }

    /** Pantalla de busqueda T9 */
    private void drawSearchScreen(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Buscar", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Buscar", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        // Campo de busqueda
        int fieldY = 22;
        g.setColor(colorGlass);
        g.fillRoundRect(4, fieldY, w - 8, fontSmall.getHeight() + 6, 4, 4);
        g.setColor(colorText);
        g.drawRoundRect(4, fieldY, w - 8, fontSmall.getHeight() + 6, 4, 4);

        g.setFont(fontSmall);
        String displayText = t9.getDisplayText();
        if (displayText.length() == 0) {
            g.setColor(colorSoftkey);
            g.drawString("Usa teclado T9...", 8, fieldY + 3, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(colorText);
            g.drawString(displayText + "_", 8, fieldY + 3, Graphics.TOP | Graphics.LEFT);
        }

        // Resultados
        int startY = fieldY + fontSmall.getHeight() + 10;
        int itemH = fontSmall.getHeight() + 4;
        int total = playlist.getTotalSongs();

        if (t9.hasInput() && total > 0) {
            g.setColor(colorSoftkey);
            g.drawString(total + " resultados", w / 2, startY - 2,
                         Graphics.TOP | Graphics.HCENTER);
            startY += fontSmall.getHeight() + 2;

            for (int i = 0; i < visibleRows - 2 && (listScroll + i) < total; i++) {
                int idx = listScroll + i;
                int y = startY + i * itemH;

                if (idx == listIndex) {
                    g.setColor(colorSelected);
                    g.fillRoundRect(2, y, w - 4, itemH - 1, 3, 3);
                }

                g.setFont(fontSmall);
                g.setColor(idx == listIndex ? colorText : colorSoftkey);
                String songTitle = playlist.getTitleAt(idx);
                if (songTitle != null) {
                    songTitle = truncateText(songTitle, fontSmall, w - 16);
                    g.drawString(songTitle, 8, y + 1, Graphics.TOP | Graphics.LEFT);
                }
            }
        } else if (t9.hasInput()) {
            g.setColor(colorSoftkey);
            g.drawString("Sin resultados", w / 2, startY,
                         Graphics.TOP | Graphics.HCENTER);
        }
    }

    /** Overlay de salto por letra */
    private void drawLetterJump(Graphics g, int w, int h) {
        // Fondo semi-transparente (dithered)
        g.setColor(colorBgTop);
        g.fillRect(0, 0, w, h);

        // Letra grande centrada
        g.setFont(fontLarge);
        String letterStr = String.valueOf(currentLetter);
        g.setColor(colorTextShadow);
        g.drawString(letterStr, w / 2 + 2, h / 2 - 15 + 2, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString(letterStr, w / 2, h / 2 - 15, Graphics.TOP | Graphics.HCENTER);

        // Texto indicador
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString("Saltando a \"" + currentLetter + "\"...",
                     w / 2, h / 2 + 15, Graphics.TOP | Graphics.HCENTER);
    }

    private void drawLoadingState(Graphics g, int w, int h) {
        g.setFont(fontSmall);
        g.setColor(colorText);
        g.drawString(loadingMessage, w / 2, h / 2, Graphics.TOP | Graphics.HCENTER);
    }

    // --- Controles ---

    protected void keyPressed(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {}

        switch (state) {
            case STATE_MENU:
                handleMenuKey(gameAction, keyCode);
                break;
            case STATE_ALL_SONGS:
            case STATE_ARTIST_SONGS:
            case STATE_ALBUM_SONGS:
            case STATE_FAVORITES:
                handleListKey(gameAction, keyCode);
                break;
            case STATE_ARTISTS:
            case STATE_ALBUMS:
                handleCategoryKey(gameAction, keyCode);
                break;
            case STATE_SEARCH:
                handleSearchKey(gameAction, keyCode);
                break;
        }

        repaint();
    }

    protected void keyRepeated(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {}

        // Salto rapido por letras al mantener arriba/abajo en la lista
        if (state == STATE_ALL_SONGS || state == STATE_ARTIST_SONGS
            || state == STATE_ALBUM_SONGS) {

            long now = System.currentTimeMillis();
            if (now - lastKeyRepeatTime < 200) {
                keyRepeatCount++;
            } else {
                keyRepeatCount = 1;
            }
            lastKeyRepeatTime = now;

            if (keyRepeatCount >= 3) {
                // Activar salto por letras
                if (gameAction == Canvas.UP) {
                    currentLetter--;
                    if (currentLetter < 'A') currentLetter = 'Z';
                } else if (gameAction == Canvas.DOWN) {
                    currentLetter++;
                    if (currentLetter > 'Z') currentLetter = 'A';
                }
                letterJumpActive = true;
                letterJumpTime = now;
                listIndex = playlist.findByLetter(currentLetter);
                adjustScroll();
                repaint();
                return;
            }
        }

        // Scroll normal rapido
        keyPressed(keyCode);
    }

    private void handleMenuKey(int gameAction, int keyCode) {
        if (gameAction == Canvas.UP) {
            menuIndex--;
            if (menuIndex < 0) menuIndex = MENU_ITEMS.length - 1;
        } else if (gameAction == Canvas.DOWN) {
            menuIndex++;
            if (menuIndex >= MENU_ITEMS.length) menuIndex = 0;
        } else if (gameAction == Canvas.FIRE) {
            selectMenuItem();
        } else if (keyCode == -6 || keyCode == -21) {
            // Atras -> volver al player
            midlet.showPlayer();
        }
    }

    private void selectMenuItem() {
        switch (menuIndex) {
            case 0: // Todas
                loading = true;
                loadingMessage = "Cargando canciones...";
                repaint();
                new Thread() {
                    public void run() {
                        try {
                            playlist.loadAll();
                        } catch (Throwable t) {
                            loadingMessage = "Error al cargar";
                            repaint();
                            try { Thread.sleep(1500); } catch (Exception e) {}
                        }
                        state = STATE_ALL_SONGS;
                        listIndex = 0;
                        listScroll = 0;
                        loading = false;
                        repaint();
                    }
                }.start();
                break;
            case 1: // Artistas
                loading = true;
                loadingMessage = "Cargando artistas...";
                repaint();
                new Thread() {
                    public void run() {
                        categoryList = client.getArtists();
                        state = STATE_ARTISTS;
                        categoryIndex = 0;
                        categoryScroll = 0;
                        loading = false;
                        repaint();
                    }
                }.start();
                break;
            case 2: // Albums
                loading = true;
                loadingMessage = "Cargando albumes...";
                repaint();
                new Thread() {
                    public void run() {
                        categoryList = client.getAlbums();
                        state = STATE_ALBUMS;
                        categoryIndex = 0;
                        categoryScroll = 0;
                        loading = false;
                        repaint();
                    }
                }.start();
                break;
            case 3: // Favoritas
                // TODO: implementar favoritas con RMS
                state = STATE_FAVORITES;
                listIndex = 0;
                listScroll = 0;
                break;
            case 4: // Buscar
                state = STATE_SEARCH;
                t9.clear();
                listIndex = 0;
                listScroll = 0;
                break;
        }
    }

    private void handleListKey(int gameAction, int keyCode) {
        int total = playlist.getTotalSongs();

        if (gameAction == Canvas.UP) {
            listIndex--;
            if (listIndex < 0) listIndex = 0;
            adjustScroll();
        } else if (gameAction == Canvas.DOWN) {
            listIndex++;
            if (listIndex >= total) listIndex = total - 1;
            adjustScroll();
        } else if (gameAction == Canvas.FIRE) {
            // Seleccionar y reproducir
            playlist.selectByIndex(listIndex);
            midlet.playSelectedSong();
        } else if (keyCode == -6 || keyCode == -21) {
            // Atras
            state = STATE_MENU;
            keyRepeatCount = 0;
        } else if (keyCode == -7 || keyCode == -22) {
            // Reproducir seleccionada
            playlist.selectByIndex(listIndex);
            midlet.playSelectedSong();
        }

        // Inicializar letra actual
        if (total > 0) {
            String title = playlist.getTitleAt(listIndex);
            if (title != null && title.length() > 0) {
                currentLetter = Character.toUpperCase(title.charAt(0));
            }
        }
    }

    private void handleCategoryKey(int gameAction, int keyCode) {
        if (categoryList == null) return;

        if (gameAction == Canvas.UP) {
            categoryIndex--;
            if (categoryIndex < 0) categoryIndex = 0;
            if (categoryIndex < categoryScroll) categoryScroll = categoryIndex;
        } else if (gameAction == Canvas.DOWN) {
            categoryIndex++;
            if (categoryIndex >= categoryList.length)
                categoryIndex = categoryList.length - 1;
            if (categoryIndex >= categoryScroll + visibleRows)
                categoryScroll = categoryIndex - visibleRows + 1;
        } else if (gameAction == Canvas.FIRE) {
            // Cargar canciones de esta categoria
            final String selected = categoryList[categoryIndex];
            loading = true;
            loadingMessage = "Cargando...";
            repaint();
            new Thread() {
                public void run() {
                    if (state == STATE_ARTISTS) {
                        playlist.loadByArtist(selected);
                        state = STATE_ARTIST_SONGS;
                    } else {
                        playlist.loadByAlbum(selected);
                        state = STATE_ALBUM_SONGS;
                    }
                    listIndex = 0;
                    listScroll = 0;
                    loading = false;
                    repaint();
                }
            }.start();
        } else if (keyCode == -6 || keyCode == -21) {
            state = STATE_MENU;
        }
    }

    private void handleSearchKey(int gameAction, int keyCode) {
        if (keyCode == -6 || keyCode == -21) {
            // Atras
            state = STATE_MENU;
            t9.clear();
            return;
        }

        if (keyCode == -7 || keyCode == -22) {
            // Borrar
            t9.backspace();
            doSearch();
            return;
        }

        // Navegacion de resultados
        if (gameAction == Canvas.UP) {
            listIndex--;
            if (listIndex < 0) listIndex = 0;
            adjustScroll();
            return;
        } else if (gameAction == Canvas.DOWN) {
            listIndex++;
            if (listIndex >= playlist.getTotalSongs())
                listIndex = playlist.getTotalSongs() - 1;
            if (listIndex < 0) listIndex = 0;
            adjustScroll();
            return;
        } else if (gameAction == Canvas.FIRE) {
            if (playlist.getTotalSongs() > 0) {
                playlist.selectByIndex(listIndex);
                midlet.playSelectedSong();
            }
            return;
        }

        // Teclas numericas para T9
        char keyChar = 0;
        if (keyCode >= Canvas.KEY_NUM0 && keyCode <= Canvas.KEY_NUM9) {
            int num = keyCode - Canvas.KEY_NUM0;
            if (t9.keyPressed(num)) {
                doSearch();
            }
        }
    }

    /** Ejecuta busqueda con el texto T9 actual */
    private void doSearch() {
        final String query = t9.getSearchText();
        if (query.length() > 0) {
            new Thread() {
                public void run() {
                    playlist.loadSearch(query);
                    listIndex = 0;
                    listScroll = 0;
                    repaint();
                }
            }.start();
        }
    }

    /** Ajusta scroll para mantener el item seleccionado visible */
    private void adjustScroll() {
        if (listIndex < listScroll) {
            listScroll = listIndex;
        }
        if (listIndex >= listScroll + visibleRows) {
            listScroll = listIndex - visibleRows + 1;
        }
    }

    // --- Utilidades de dibujo (compartidas con PlayerCanvas) ---

    private void drawGradient(Graphics g, int x, int y, int w, int h,
                              int colorTop, int colorBottom) {
        int rT = (colorTop >> 16) & 0xFF;
        int gT = (colorTop >> 8) & 0xFF;
        int bT = colorTop & 0xFF;
        int rB = (colorBottom >> 16) & 0xFF;
        int gB = (colorBottom >> 8) & 0xFF;
        int bB = colorBottom & 0xFF;

        for (int i = 0; i < h; i += 4) {
            int r = rT + (rB - rT) * i / h;
            int gr = gT + (gB - gT) * i / h;
            int b = bT + (bB - bT) * i / h;
            g.setColor((r << 16) | (gr << 8) | b);
            int blockH = Math.min(4, h - i);
            g.fillRect(x, y + i, w, blockH);
        }
    }

    private void drawGlassEffect(Graphics g, int x, int y, int w, int h) {
        g.setColor(colorGlass);
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col += 2) {
                int offset = (row % 2 == 0) ? 0 : 1;
                g.drawLine(col + offset, row, col + offset, row);
            }
        }
        g.setColor(0xFFFFFF);
        for (int col = x; col < x + w; col += 2) {
            g.drawLine(col, y + h - 1, col, y + h - 1);
        }
    }

    private String truncateText(String text, Font font, int maxWidth) {
        if (text == null) return "";
        if (font.stringWidth(text) <= maxWidth) return text;
        while (text.length() > 0 && font.stringWidth(text + "..") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private int colorBarBg() {
        switch (settings.theme) {
            case Settings.THEME_DARK: return 0x2A2A3A;
            case Settings.THEME_NOKIA: return 0x002266;
            default: return 0x003355;
        }
    }

    /** Resetea al menu principal */
    public void resetToMenu() {
        state = STATE_MENU;
        keyRepeatCount = 0;
        letterJumpActive = false;
    }
}
