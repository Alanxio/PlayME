package playme;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Canvas principal del reproductor. Dibuja la interfaz estilo Windows Vista
 * Aero en 128x160px.
 *
 * Layout (128x160): - Header: "Musica" con efecto cristal (0-20) - Cover art:
 * 48x48 centrado (22-72) - Titulo + Artista (74-100) - Barra de progreso
 * (102-114) - Controles (116-140) - Softkeys (142-160)
 */
public class PlayerCanvas extends Canvas implements Runnable {

    private MusicPlayerMIDlet midlet;
    private MusicService musicService;
    private PlaylistManager playlist;
    private Settings settings;
    private ServerClient client;

    // Cancion actual
    private Song currentSong;
    private Image coverImage;
    private boolean loadingCover;
    private int coverSize = 48; // Tamaño dinámico de la carátula

    // Estado UI
    private boolean needsRepaint = true;
    private boolean running = true;
    private Thread uiThread;

    // Colores tema Aero Vista
    private int colorBgTop;
    private int colorBgBottom;
    private int colorGlass;
    private int colorText;
    private int colorTextShadow;
    private int colorAccent;
    private int colorBarBg;
    private int colorBarFill;
    private int colorSoftkey;

    // Long press detection
    private long okPressTime;
    private boolean okHeld;
    private static final long LONG_PRESS_MS = 600;

    // Animacion
    private int progressGlow = 0;
    private boolean glowUp = true;

    // Fonts
    private Font fontSmall;
    private Font fontMedium;
    private Font fontLarge;

    // Estado de carga
    private String statusMessage = "Conectando...";
    private String errorDetail = null;
    private boolean isLoading = true;
    private String coverMessage = null;

    // Progreso de descarga del audio
    private boolean isDownloading = false;
    private int downloadPercent = 0;
    private String downloadMessage = null;

    // Scroll horizontal para mensajes largos (errores)
    private int errorScrollOffset = 0;
    private long lastScrollTime = 0;

    public PlayerCanvas(MusicPlayerMIDlet midlet, MusicService musicService,
            PlaylistManager playlist, Settings settings,
            ServerClient client) {
        this.midlet = midlet;
        this.musicService = musicService;
        this.playlist = playlist;
        this.settings = settings;
        this.client = client;

        setFullScreenMode(true);
        applyTheme();

        fontSmall = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontMedium = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
        fontLarge = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);

        // Hilo de actualizacion UI (repaint cada 500ms para el timer)
        uiThread = new Thread(this);
        uiThread.start();
    }

    /**
     * Aplica colores segun tema seleccionado
     */
    public void applyTheme() {
        switch (settings.theme) {
            case Settings.THEME_DARK:
                colorBgTop = 0x1A1A2E;
                colorBgBottom = 0x0F0F1A;
                colorGlass = 0x2A2A4A;
                colorText = 0xE0E0E0;
                colorTextShadow = 0x000000;
                colorAccent = 0x6C63FF;
                colorBarBg = 0x2A2A3A;
                colorBarFill = 0x6C63FF;
                colorSoftkey = 0x8888AA;
                break;
            case Settings.THEME_NOKIA:
                colorBgTop = 0x003399;
                colorBgBottom = 0x001A66;
                colorGlass = 0x1A5CCC;
                colorText = 0xFFFFFF;
                colorTextShadow = 0x001133;
                colorAccent = 0x00AAFF;
                colorBarBg = 0x002266;
                colorBarFill = 0x00AAFF;
                colorSoftkey = 0x88BBEE;
                break;
            default: // THEME_AERO
                colorBgTop = 0x0052A5;
                colorBgBottom = 0x003366;
                colorGlass = 0x3399DD;
                colorText = 0xFFFFFF;
                colorTextShadow = 0x002244;
                colorAccent = 0x44BBFF;
                colorBarBg = 0x003355;
                colorBarFill = 0x44BBFF;
                colorSoftkey = 0xAADDFF;
                break;
        }
    }

    /**
     * Actualiza la canción mostrada
     */
    public void updateCurrentSong(Song song) {
        this.currentSong = song;
        this.coverImage = null;
        this.coverMessage = null;
        this.isLoading = false;
        this.statusMessage = null;

        // Calcular tamaño de carátula antes de cargarla
        calculateCoverSize();

        if (song != null && settings.showCovers && song.coverFile != null) {
            this.coverMessage = "Descargando portada...";
            loadCoverAsync(song.coverFile);
        } else if (song != null && settings.showCovers) {
            this.coverMessage = "Sin portada";
        }
        needsRepaint = true;
    }

    /**
     * Calcula el tamaño óptimo de la carátula basado en el layout
     */
    private void calculateCoverSize() {
        int w = getWidth();
        int h = getHeight();
        int headerH = 20;
        int coverMarginTop = 3;
        int fmH = fontMedium.getHeight();
        int fsH = fontSmall.getHeight();
        int softkeyH = fsH + 4;
        int ctrlGap = 6;

        // Espacio fijo necesario debajo de la portada
        int belowCoverFixed = 4 // gap portada -> titulo
                + fmH + 2 // titulo + gap
                + fsH + 3 // artista + gap
                + fsH + 3 // tiempo + gap
                + 6 + ctrlGap // barra + gap
                + fmH; // controles

        int available = h - headerH - coverMarginTop - softkeyH - belowCoverFixed;
        coverSize = Math.min(64, Math.max(40, available));
        coverSize = Math.min(coverSize, w - 16); // margen lateral minimo
    }

    /**
     * Muestra mensaje de carga
     */
    public void setLoading(String msg) {
        this.isLoading = true;
        this.statusMessage = msg;
        this.errorDetail = null;
        this.errorScrollOffset = 0;
        this.lastScrollTime = 0;
        this.isDownloading = false;
        this.downloadPercent = 0;
        this.downloadMessage = null;
        needsRepaint = true;
    }

    /**
     * Muestra error con detalle concreto de Java
     */
    public void setError(String msg, String detail) {
        this.isLoading = true;
        // Mostrar el error real de Java como mensaje principal
        String display = (detail != null && detail.length() > 0) ? detail : msg;
        this.statusMessage = display;
        this.errorDetail = null;
        this.errorScrollOffset = 0;
        this.lastScrollTime = 0;
        needsRepaint = true;
    }

    /**
     * Limpia el estado de carga manteniendo la cancion actual
     */
    public void clearLoading() {
        this.isLoading = false;
        this.statusMessage = null;
        this.errorDetail = null;
        this.errorScrollOffset = 0;
        this.lastScrollTime = 0;
        this.isDownloading = false;
        this.downloadPercent = 0;
        this.downloadMessage = null;
        needsRepaint = true;
    }

    /**
     * Actualiza el progreso de descarga del audio.
     */
    public void setDownloadProgress(int percent, int downloaded, int total) {
        this.isDownloading = true;
        this.downloadPercent = percent;
        this.downloadMessage = "Descargando " + percent + "%";
        needsRepaint = true;
        repaint();
    }

    /**
     * Carga portada en hilo separado
     */
    private void loadCoverAsync(final String coverFile) {
        if (loadingCover) {
            return;
        }
        loadingCover = true;
        final int targetSize = coverSize; // Capturar el tamaño calculado
        new Thread() {
            public void run() {
                Image scaled = null;
                try {
                    // Pedir cover del tamaño calculado dinámicamente
                    byte[] data = client.downloadCover(coverFile, targetSize);
                    if (data != null) {
                        // Liberar memoria antes de crear imagen
                        System.gc();
                        Image img = Image.createImage(data, 0, data.length);
                        // La imagen ya viene al tamaño correcto, pero nos aseguramos
                        if (img.getWidth() != targetSize || img.getHeight() != targetSize) {
                            scaled = scaleImage(img, targetSize, targetSize);
                            img = null;
                        } else {
                            scaled = img;
                        }
                        data = null;
                    }
                } catch (OutOfMemoryError oom) {
                    System.out.println("[PlayerCanvas] OutOfMemory cargando cover");
                    scaled = null;
                    System.gc();
                } catch (Throwable t) {
                    System.out.println("[PlayerCanvas] Error cover: " + t.getClass().getName());
                    scaled = null;
                }
                coverImage = scaled;
                if (scaled == null && coverMessage != null && coverMessage.equals("Descargando portada...")) {
                    coverMessage = "Sin portada";
                } else {
                    coverMessage = null;
                }
                loadingCover = false;
                needsRepaint = true;
                repaint();
            }
        }.start();
    }

    /**
     * Escala imagen simple para J2ME
     */
    private Image scaleImage(Image src, int dw, int dh) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw == dw && sh == dh) {
            return src;
        }

        int[] srcPixels = new int[sw * sh];
        src.getRGB(srcPixels, 0, sw, 0, 0, sw, sh);

        int[] dstPixels = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int srcY = y * sh / dh;
            for (int x = 0; x < dw; x++) {
                int srcX = x * sw / dw;
                dstPixels[y * dw + x] = srcPixels[srcY * sw + srcX];
            }
        }

        srcPixels = null;
        return Image.createRGBImage(dstPixels, dw, dh, true);
    }

    // --- Dibujo ---
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // 1. Fondo degradado simplificado
        drawGradient(g, 0, 0, w, h, colorBgTop, colorBgBottom);

        // 2. Header cristal simplificado
        drawGlassEffect(g, 0, 0, w, 20);

        if (isLoading) {
            drawLoadingScreen(g, w, h);
            return;
        }

        if (currentSong == null) {
            drawNoSongScreen(g, w, h);
            return;
        }

        // Layout adaptativo para 128x160 y pantallas similares.
        // La portada se calcula dinámicamente para aprovechar al máximo
        // el espacio disponible (antes era un tamaño fijo de 48px).
        int headerH = 20;
        int coverMarginTop = 3;
        int fmH = fontMedium.getHeight();
        int fsH = fontSmall.getHeight();
        int softkeyH = fsH + 4;
        int ctrlGap = 6;

        // Usar coverSize calculado previamente en calculateCoverSize()
        int coverX = (w - coverSize) / 2;
        int coverY = headerH + coverMarginTop;

        // 3. Header "Musica" + indicador de volumen (altavoz con ondas)
        g.setFont(fontMedium);
        drawShadowText(g, "Musica", w / 2, 3, Graphics.TOP | Graphics.HCENTER);
        drawVolumeIndicator(g, w, headerH);

        // 4. Portada centrada
        // Sombra de la portada (solo si cabe)
        if (coverSize >= 48) {
            g.setColor(0x000000);
            g.fillRect(coverX + 2, coverY + 2, coverSize, coverSize);
        }

        if (coverImage != null) {
            // Borde blanco
            g.setColor(0xFFFFFF);
            g.drawRect(coverX - 1, coverY - 1, coverSize, coverSize);
            g.drawImage(coverImage, coverX, coverY, Graphics.TOP | Graphics.LEFT);
        } else {
            // Placeholder: cuadrado con nota musical
            g.setColor(colorGlass);
            g.fillRect(coverX, coverY, coverSize, coverSize);
            g.setColor(0xFFFFFF);
            g.drawRect(coverX - 1, coverY - 1, coverSize, coverSize);
            g.setFont(fontLarge);
            g.drawString("\u266B", w / 2, coverY + coverSize / 2 - 8, Graphics.TOP | Graphics.HCENTER);
        }

        // 5. Mensaje de estado del cover (cargando / sin portada)
        if (coverMessage != null) {
            g.setFont(fontSmall);
            g.setColor(colorSoftkey);
            g.drawString(coverMessage, w / 2, coverY + coverSize + 2, Graphics.TOP | Graphics.HCENTER);
        }

        // 6. Titulo y artista
        int textY = coverY + coverSize + 4;
        if (coverMessage != null) {
            textY += fsH + 2;
        }
        g.setFont(fontMedium);
        String title = truncateText(currentSong.title, fontMedium, w - 8);
        drawShadowText(g, title, w / 2, textY, Graphics.TOP | Graphics.HCENTER);

        textY += fmH + 2;
        g.setFont(fontSmall);
        String artist = truncateText(currentSong.artist, fontSmall, w - 8);
        g.setColor(colorSoftkey);
        g.drawString(artist, w / 2, textY, Graphics.TOP | Graphics.HCENTER);

        // 7. Tiempo
        textY += fsH + 3;
        int pos = musicService.getCurrentPosition();
        int dur = currentSong.duration > 0 ? currentSong.duration : musicService.getDurationSeconds();
        String timeStr = Song.formatTime(pos) + " / " + Song.formatTime(dur);
        g.setFont(fontSmall);
        g.setColor(colorText);
        g.drawString(timeStr, w / 2, textY, Graphics.TOP | Graphics.HCENTER);

        // 8. Barra de progreso de descarga (solo mientras se descarga)
        int barY = textY + fsH + 4;
        int barX = 8;
        int barW = w - 16;
        int barH = 6;

        if (isDownloading) {
            g.setFont(fontSmall);
            g.setColor(colorSoftkey);
            g.drawString(downloadMessage != null ? downloadMessage : "Descargando...", w / 2, barY, Graphics.TOP | Graphics.HCENTER);
            barY += fsH + 2;
            drawDownloadBar(g, barX, barY, barW, 4, downloadPercent);
            barY += 4 + 4; // barra de descarga + gap
        }

        // 9. Barra de progreso de reproduccion
        barY += 1;
        // Red de seguridad: con el calculo dinamico de la portada normalmente
        // no hace falta, pero evita overflow si las metricas de fuente varian
        int reserveBelowBar = ctrlGap + fmH + 4;
        if (barY + barH + reserveBelowBar > h - softkeyH) {
            barY = h - softkeyH - reserveBelowBar - barH;
        }
        drawProgressBar(g, barX, barY, barW, barH, pos, dur);

        // 9. Controles
        int ctrlY = barY + barH + ctrlGap;
        if (ctrlY + fmH > h - softkeyH) {
            ctrlY = h - softkeyH - fmH;
        }
        drawControls(g, w, ctrlY);

        // 10. Estado (shuffle, repeat, velocidad)
        int statusY = ctrlY + fmH + 3;
        if (statusY + fsH <= h - softkeyH) {
            drawStatusIcons(g, w, statusY);
        }

        // 11. Softkeys
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString("Opc.", 2, h - 2, Graphics.BOTTOM | Graphics.LEFT);
        g.drawString("Lista", w - 2, h - 2, Graphics.BOTTOM | Graphics.RIGHT);
    }

    /**
     * Dibuja degradado vertical simplificado
     */
    private void drawGradient(Graphics g, int x, int y, int w, int h,
            int colorTop, int colorBottom) {
        int rT = (colorTop >> 16) & 0xFF;
        int gT = (colorTop >> 8) & 0xFF;
        int bT = colorTop & 0xFF;
        int rB = (colorBottom >> 16) & 0xFF;
        int gB = (colorBottom >> 8) & 0xFF;
        int bB = colorBottom & 0xFF;

        // Bloques de 8 lineas para reducir llamadas a fillRect
        for (int i = 0; i < h; i += 8) {
            int r = rT + (rB - rT) * i / h;
            int gr = gT + (gB - gT) * i / h;
            int b = bT + (bB - bT) * i / h;
            g.setColor((r << 16) | (gr << 8) | b);
            int blockH = Math.min(8, h - i);
            g.fillRect(x, y + i, w, blockH);
        }
    }

    /**
     * Efecto cristal Aero simplificado
     */
    private void drawGlassEffect(Graphics g, int x, int y, int w, int h) {
        // Franja superior de color glass sólido
        g.setColor(colorGlass);
        g.fillRect(x, y, w, h);
        // Linea de brillo inferior
        g.setColor(0xFFFFFF);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
    }

    /**
     * Dibuja texto con sombra
     */
    private void drawShadowText(Graphics g, String text, int x, int y, int anchor) {
        g.setColor(colorTextShadow);
        g.drawString(text, x + 1, y + 1, anchor);
        g.setColor(colorText);
        g.drawString(text, x, y, anchor);
    }

    /**
     * Barra de progreso con efecto Aero
     */
    private void drawProgressBar(Graphics g, int x, int y, int w, int h,
            int pos, int dur) {
        // Fondo
        g.setColor(colorBarBg);
        g.fillRoundRect(x, y, w, h, 3, 3);

        // Relleno
        if (dur > 0) {
            int fillW = (int) ((long) pos * w / dur);
            if (fillW > w) {
                fillW = w;
            }
            if (fillW > 0) {
                g.setColor(colorBarFill);
                g.fillRoundRect(x, y, fillW, h, 3, 3);

                // Efecto brillo en la barra (linea superior mas clara)
                int glowColor = brighten(colorBarFill, 40 + progressGlow);
                g.setColor(glowColor);
                g.drawLine(x + 1, y + 1, x + fillW - 1, y + 1);
            }
        }

        // Borde
        g.setColor(colorText);
        g.drawRoundRect(x, y, w, h, 3, 3);
    }

    /**
     * Barra de progreso de descarga del audio.
     */
    private void drawDownloadBar(Graphics g, int x, int y, int w, int h, int percent) {
        // Fondo
        g.setColor(colorBarBg);
        g.fillRect(x, y, w, h);

        // Relleno
        int fillW = (int) ((long) percent * w / 100);
        if (fillW > w) fillW = w;
        if (fillW > 0) {
            g.setColor(colorAccent);
            g.fillRect(x, y, fillW, h);
        }

        // Borde
        g.setColor(colorText);
        g.drawRect(x, y, w, h);
    }

    /**
     * Controles de reproduccion
     */
    private void drawControls(Graphics g, int w, int y) {
        g.setFont(fontMedium);
        int centerX = w / 2;

        // <<
        g.setColor(colorSoftkey);
        g.drawString("<<", centerX - 30, y, Graphics.TOP | Graphics.HCENTER);

        // Play/Pause
        g.setColor(colorText);
        String playSymbol = musicService.isPlaying() ? "||" : ">";
        g.drawString(playSymbol, centerX, y, Graphics.TOP | Graphics.HCENTER);

        // >>
        g.setColor(colorSoftkey);
        g.drawString(">>", centerX + 30, y, Graphics.TOP | Graphics.HCENTER);
    }

    /**
     * Iconos de estado (shuffle, repeat, velocidad)
     */
    private void drawStatusIcons(Graphics g, int w, int y) {
        g.setFont(fontSmall);
        StringBuffer status = new StringBuffer();

        if (settings.shuffle) {
            status.append("\u2694 "); // shuffle
        }
        if (settings.repeat == Settings.REPEAT_ONE) {
            status.append("R1 ");
        } else if (settings.repeat == Settings.REPEAT_ALL) {
            status.append("RA ");
        }
        if (musicService.isDoubleSpeed()) {
            status.append("x2");
        }

        if (status.length() > 0) {
            g.setColor(colorAccent);
            g.drawString(status.toString(), w / 2, y,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    /**
     * Pantalla de carga
     */
    private void drawLoadingScreen(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        drawShadowText(g, "PlayME", w / 2, h / 2 - 30, Graphics.TOP | Graphics.HCENTER);

        if (statusMessage != null) {
            g.setFont(fontSmall);
            g.setColor(colorSoftkey);

            int maxTextW = w - 4;
            int textW = fontSmall.stringWidth(statusMessage);
            int msgY = h / 2 - 5;

            if (textW <= maxTextW) {
                // Cabe completo: centrado
                g.drawString(statusMessage, w / 2, msgY, Graphics.TOP | Graphics.HCENTER);
            } else {
                // No cabe: scroll horizontal
                long now = System.currentTimeMillis();
                if (now - lastScrollTime > 40) {
                    errorScrollOffset += 4;
                    // Reiniciar cuando el texto haya salido por completo
                    if (errorScrollOffset > textW + 10) {
                        errorScrollOffset = -maxTextW;
                    }
                    lastScrollTime = now;
                }
                g.setClip(2, msgY - 2, maxTextW, fontSmall.getHeight() + 4);
                g.drawString(statusMessage, 2 - errorScrollOffset, msgY, Graphics.TOP | Graphics.LEFT);
                g.setClip(0, 0, w, h);
            }
        }

        // Barra de progreso de descarga dentro de la pantalla de carga
        if (isDownloading) {
            int barX = 12;
            int barW = w - 24;
            int barH = 6;
            int barY = h / 2 + 12;

            // Texto de porcentaje
            g.setFont(fontSmall);
            g.setColor(colorSoftkey);
            String dlText = downloadMessage != null ? downloadMessage : "Descargando...";
            g.drawString(dlText, w / 2, barY, Graphics.TOP | Graphics.HCENTER);

            barY += fontSmall.getHeight() + 3;

            // Barra visual
            drawDownloadBar(g, barX, barY, barW, barH, downloadPercent);
        } else if (errorDetail == null && (statusMessage == null || fontSmall.stringWidth(statusMessage) <= w - 4)) {
            // Animacion de carga: puntos (solo si no hay error ni descarga activa)
            g.setColor(colorAccent);
            int dots = (int) ((System.currentTimeMillis() / 500) % 4);
            StringBuffer dotsStr = new StringBuffer();
            for (int i = 0; i < dots; i++) {
                dotsStr.append('.');
            }
            g.drawString(dotsStr.toString(), w / 2, h / 2 + 12, Graphics.TOP | Graphics.HCENTER);
        }
    }

    /**
     * Pantalla sin cancion
     */
    private void drawNoSongScreen(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        drawShadowText(g, "PlayME", w / 2, 4, Graphics.TOP | Graphics.HCENTER);

        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString("Sin canciones", w / 2, h / 2 - 5, Graphics.TOP | Graphics.HCENTER);
        g.drawString("Pulsa Lista", w / 2, h / 2 + 10, Graphics.TOP | Graphics.HCENTER);

        // Softkeys
        g.drawString("Opc.", 2, h - 2, Graphics.BOTTOM | Graphics.LEFT);
        g.drawString("Lista", w - 2, h - 2, Graphics.BOTTOM | Graphics.RIGHT);
    }

    /**
     * Trunca texto si excede ancho
     */
    private String truncateText(String text, Font font, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.stringWidth(text) <= maxWidth) {
            return text;
        }
        while (text.length() > 0 && font.stringWidth(text + "..") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    /**
     * Aclara un color
     */
    private int brighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (r << 16) | (gr << 8) | b;
    }

    // --- Controles ---
    protected void keyPressed(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {
        }

        // FIRE / OK - pulsacion corta
        if (gameAction == Canvas.FIRE) {
            okPressTime = System.currentTimeMillis();
            okHeld = false;
            return;
        }

        // Joystick
        if (gameAction == Canvas.LEFT) {
            playPrevious();
        } else if (gameAction == Canvas.RIGHT) {
            playNext();
        } else if (gameAction == Canvas.UP) {
            adjustVolume(+10);
        } else if (gameAction == Canvas.DOWN) {
            adjustVolume(-10);
        }

        // Teclas numericas: seek
        if (keyCode == Canvas.KEY_NUM4) {
            musicService.seek(-10); // retroceder 10s
        } else if (keyCode == Canvas.KEY_NUM6) {
            musicService.seek(10); // avanzar 10s
        }

        // Softkeys
        if (keyCode == -6 || keyCode == -21) {
            // Softkey izquierda -> Opciones/Ajustes
            midlet.showSettings();
        } else if (keyCode == -7 || keyCode == -22) {
            // Softkey derecha -> Lista/Biblioteca
            midlet.showLibrary();
        }

        needsRepaint = true;
        repaint();
    }

    protected void keyReleased(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {
        }

        if (gameAction == Canvas.FIRE) {
            long duration = System.currentTimeMillis() - okPressTime;
            if (duration >= LONG_PRESS_MS) {
                // Long press -> velocidad x2
                musicService.toggleDoubleSpeed();
            } else {
                // Short press -> play/pause
                if (currentSong != null && !musicService.isBuffering()) {
                    if (musicService.isPlaying() || musicService.isPaused()) {
                        musicService.togglePause();
                    } else {
                        // No hay nada reproduciendose, iniciar
                        playCurrentSong();
                    }
                }
            }
            needsRepaint = true;
            repaint();
        }
    }

    protected void keyRepeated(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {
        }

        // Mantener arriba/abajo = ajustar volumen rapido
        if (gameAction == Canvas.UP) {
            adjustVolume(+10);
        } else if (gameAction == Canvas.DOWN) {
            adjustVolume(-10);
        }
    }

    /**
     * Reproduce la cancion actual del playlist
     */
    public void playCurrentSong() {
        if (!playlist.isLoaded()) {
            return;
        }
        Song song = playlist.getCurrentSong();
        if (song == null) {
            return;
        }
        updateCurrentSong(song);
        int bitrate = settings.calculateBitrate(song.duration);
        musicService.playSong(song.id, bitrate, song.mimeType);
    }

    /**
     * Ajusta el volumen y guarda el valor
     */
    private void adjustVolume(int delta) {
        int newVol = settings.volume + delta;
        if (newVol < 0) {
            newVol = 0;
        }
        if (newVol > 100) {
            newVol = 100;
        }
        settings.volume = newVol;
        settings.save();
        musicService.setVolume(newVol);
        needsRepaint = true;
        repaint();
    }

    /**
     * Dibuja un altavoz con ondas de sonido (en vez de texto "%") en la esquina
     * superior derecha del header. Estilizado con el mismo lenguaje visual Aero
     * del resto de la app: sombra suave (igual que drawShadowText), un borde de
     * acento a modo de brillo de cristal, y ondas con el mismo pulso animado
     * (progressGlow) que ya usa la barra de progreso. Al vivir dentro de la
     * franja fija del header (20px) nunca pisa el resto del layout, que es
     * variable segun el tamano de la portada.
     */
    private void drawVolumeIndicator(Graphics g, int w, int headerH) {
        int vol = settings.volume;

        int iconH = 8;
        int backW = 3;
        int coneW = 6;
        int leftEdgeH = 4;          // alto del lado estrecho (pegado a la caja)
        int x = w - 24;
        int y = (headerH - iconH) / 2;
        int coneTopY = y + (iconH - leftEdgeH) / 2;     // y + 2
        int coneBottomY = coneTopY + leftEdgeH;          // y + 6
        int coneX1 = x + backW;      // lado estrecho (izquierda)
        int coneX2 = x + backW + coneW; // lado ancho y plano (derecha)

        // Sombra suave detras del icono, el mismo recurso que drawShadowText
        // usa para todo el texto de la app, asi el altavoz tiene la misma
        // sensacion de profundidad que el resto de la interfaz.
        g.setColor(colorTextShadow);
        fillSpeakerCone(g, x + 1, y + 1, iconH, backW, coneW, leftEdgeH);

        // Cuerpo solido
        g.setColor(colorText);
        fillSpeakerCone(g, x, y, iconH, backW, coneW, leftEdgeH);

        // Borde de acento trazando el contorno: el "brillo cristal" Aero,
        // igual que el resto de la UI usa colorAccent para resaltar bordes
        g.setColor(colorAccent);
        g.drawLine(x, coneTopY, x, coneBottomY);              // lado izq. caja
        g.drawLine(x, coneTopY, coneX1, coneTopY);             // arriba caja
        g.drawLine(coneX1, coneTopY, coneX2, y);               // pendiente sup.
        g.drawLine(coneX2, y, coneX2, y + iconH);               // borde plano
        g.drawLine(coneX2, y + iconH, coneX1, coneBottomY);    // pendiente inf.
        g.drawLine(coneX1, coneBottomY, x, coneBottomY);        // abajo caja

        if (vol <= 0) {
            // Silenciado: una pequena X atenuada en lugar de ondas
            g.setColor(colorSoftkey);
            int mx = coneX2 + 3;
            int my = y + 1;
            g.drawLine(mx, my, mx + 6, my + 6);
            g.drawLine(mx + 6, my, mx, my + 6);
            return;
        }

        // Ondas de sonido (arcos concentricos) en vez de "NN%". Se encienden
        // progresivamente segun el nivel: 1 onda = bajo, 2 = medio, 3 = alto.
        // Las ondas activas llevan el mismo pulso animado (progressGlow) que
        // ya usa la barra de progreso, para un brillo "vivo" consistente.
        int waveX = coneX2;
        int waveCY = y + iconH / 2;
        int[] radii = {3, 6, 9};
        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            boolean active = vol > i * 33;
            if (active) {
                g.setColor(brighten(colorAccent, progressGlow));
                g.drawArc(waveX - r, waveCY - r, r * 2, r * 2, -50, 100);
                g.drawArc(waveX - r - 1, waveCY - r - 1, r * 2 + 2, r * 2 + 2, -50, 100);
            } else {
                g.setColor(colorBarBg);
                g.drawArc(waveX - r, waveCY - r, r * 2, r * 2, -50, 100);
            }
        }
    }

    /**
     * Rellena la silueta del altavoz (caja + cono trapezoidal) en (x,y)
     */
    private void fillSpeakerCone(Graphics g, int x, int y, int iconH, int backW, int coneW, int leftEdgeH) {
        int coneTopY = y + (iconH - leftEdgeH) / 2;
        int coneBottomY = coneTopY + leftEdgeH;
        int coneX1 = x + backW;
        int coneX2 = x + backW + coneW;
        g.fillRect(x, coneTopY, backW, leftEdgeH);
        g.fillTriangle(coneX1, coneTopY, coneX1, coneBottomY, coneX2, y);
        g.fillTriangle(coneX1, coneBottomY, coneX2, y + iconH, coneX2, y);
    }

    /**
     * Siguiente cancion
     */
    private void playNext() {
        if (playlist.next()) {
            playCurrentSong();
        }
    }

    /**
     * Cancion anterior
     */
    private void playPrevious() {
        // Si esta a mas de 3 segundos, reiniciar la cancion
        if (musicService.getCurrentPosition() > 3) {
            musicService.seek(-musicService.getCurrentPosition());
        } else if (playlist.previous()) {
            playCurrentSong();
        }
    }

    /**
     * Llamado cuando la cancion termina
     */
    public void onSongComplete() {
        if (settings.repeat == Settings.REPEAT_ONE) {
            playCurrentSong();
        } else if (playlist.next()) {
            playCurrentSong();
        } else {
            // No hay siguiente cancion y no esta en bucle:
            // limpiar los chunks de RMS para evitar acumulacion
            musicService.stopAndCleanup();
        }
    }

    // --- Hilo de actualizacion UI ---
    public void run() {
        while (running) {
            try {
                // Actualizar animaciones
                if (glowUp) {
                    progressGlow += 5;
                    if (progressGlow >= 30) {
                        glowUp = false;
                    }
                } else {
                    progressGlow -= 5;
                    if (progressGlow <= 0) {
                        glowUp = true;
                    }
                }

                if (musicService.isPlaying() || musicService.isBuffering() || isLoading || needsRepaint) {
                    repaint();
                    needsRepaint = false;
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Detiene el hilo de actualizacion
     */
    public void stop() {
        running = false;
        if (uiThread != null) {
            uiThread.interrupt();
        }
    }
}
