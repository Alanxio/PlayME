package playme;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Canvas de ajustes con estilo Aero Vista.
 * Menus:
 * - Repeticion (Off / Una / Todas)
 * - Mezcla (On / Off)
 * - Ecualizador (Normal / Bass / Rock / Pop / Clasica)
 * - Tema (Aero / Oscuro / Nokia)
 * - Streaming (Perfil de calidad / Precarga / Portadas)
 * - Teclas (info de controles)
 */
public class SettingsCanvas extends Canvas {

    private MusicPlayerMIDlet midlet;
    private Settings settings;

    // Menu principal de ajustes
    private static final String[] MENU_ITEMS = {
        "Repeticion", "Mezcla", "Ecualizador", "Tema",
        "Streaming", "Teclas"
    };

    // Sub-menu streaming
    private static final String[] STREAMING_ITEMS = {
        "Perfil calidad", "Precarga", "Mostrar portadas"
    };

    // Estados
    private static final int STATE_MAIN = 0;
    private static final int STATE_REPEAT = 1;
    private static final int STATE_SHUFFLE = 2;
    private static final int STATE_EQ = 3;
    private static final int STATE_THEME = 4;
    private static final int STATE_STREAMING = 5;
    private static final int STATE_KEYS = 6;
    private static final int STATE_QUALITY = 7;
    private static final int STATE_PRELOAD = 8;
    private static final int STATE_COVERS = 9;

    private int state = STATE_MAIN;
    private int menuIndex = 0;
    private int subIndex = 0;

    // Fonts
    private Font fontSmall;
    private Font fontMedium;

    // Colores
    private int colorBgTop;
    private int colorBgBottom;
    private int colorGlass;
    private int colorText;
    private int colorTextShadow;
    private int colorAccent;
    private int colorSelected;
    private int colorSoftkey;

    public SettingsCanvas(MusicPlayerMIDlet midlet, Settings settings) {
        this.midlet = midlet;
        this.settings = settings;

        setFullScreenMode(true);
        applyTheme();

        fontSmall = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontMedium = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
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
            default:
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

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        drawGradient(g, 0, 0, w, h, colorBgTop, colorBgBottom);
        drawGlassEffect(g, 0, 0, w, 20);

        switch (state) {
            case STATE_MAIN:
                drawMainMenu(g, w, h);
                break;
            case STATE_REPEAT:
                drawOptionList(g, w, h, "Repeticion",
                               Settings.REPEAT_LABELS, settings.repeat);
                break;
            case STATE_SHUFFLE:
                drawOptionList(g, w, h, "Mezcla",
                               new String[]{"Activada", "Desactivada"},
                               settings.shuffle ? 0 : 1);
                break;
            case STATE_EQ:
                drawOptionList(g, w, h, "Ecualizador",
                               Settings.EQ_LABELS, settings.equalizer);
                break;
            case STATE_THEME:
                drawOptionList(g, w, h, "Tema",
                               Settings.THEME_LABELS, settings.theme);
                break;
            case STATE_STREAMING:
                drawStreamingMenu(g, w, h);
                break;
            case STATE_KEYS:
                drawKeysInfo(g, w, h);
                break;
            case STATE_QUALITY:
                drawProfileList(g, w, h);
                break;
            case STATE_PRELOAD:
                drawOptionList(g, w, h, "Precarga",
                               Settings.PRELOAD_LABELS, settings.preload);
                break;
            case STATE_COVERS:
                drawOptionList(g, w, h, "Portadas",
                               Settings.BOOL_LABELS,
                               settings.showCovers ? 0 : 1);
                break;
        }

        // Softkeys
        g.setFont(fontSmall);
        g.setColor(colorSoftkey);
        g.drawString("Atras", 2, h - 2, Graphics.BOTTOM | Graphics.LEFT);
        if (state != STATE_KEYS) {
            g.drawString("OK", w - 2, h - 2, Graphics.BOTTOM | Graphics.RIGHT);
        }
    }

    private void drawMainMenu(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Ajustes", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Ajustes", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        int startY = 24;
        int itemH = fontSmall.getHeight() + 8;

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int y = startY + i * itemH;

            if (i == menuIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(4, y, w - 8, itemH - 2, 4, 4);
                g.setColor(colorAccent);
                g.drawLine(6, y + 1, w - 6, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(i == menuIndex ? colorText : colorSoftkey);
            g.drawString(MENU_ITEMS[i], 12, y + 2, Graphics.TOP | Graphics.LEFT);

            // Valor actual
            String value = getCurrentValue(i);
            if (value != null) {
                g.setColor(colorAccent);
                g.drawString(value, w - 12, y + 2, Graphics.TOP | Graphics.RIGHT);
            }
        }
    }

    /** Obtiene valor actual de un ajuste para mostrar en el menu */
    private String getCurrentValue(int menuItem) {
        switch (menuItem) {
            case 0: return Settings.REPEAT_LABELS[settings.repeat];
            case 1: return settings.shuffle ? "Si" : "No";
            case 2: return Settings.EQ_LABELS[settings.equalizer];
            case 3: return Settings.THEME_LABELS[settings.theme];
            case 4: return Settings.PROFILE_LABELS[settings.qualityProfile];
            default: return null;
        }
    }

    /** Dibuja una lista de opciones con seleccion */
    private void drawOptionList(Graphics g, int w, int h, String title,
                                 String[] options, int selected) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString(title, w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString(title, w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        int startY = 28;
        int itemH = fontSmall.getHeight() + 8;

        for (int i = 0; i < options.length; i++) {
            int y = startY + i * itemH;

            if (i == subIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(4, y, w - 8, itemH - 2, 4, 4);
                g.setColor(colorAccent);
                g.drawLine(6, y + 1, w - 6, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(i == subIndex ? colorText : colorSoftkey);

            // Indicador de seleccion actual
            String prefix = (i == selected) ? "\u2022 " : "  ";
            g.drawString(prefix + options[i], 12, y + 2, Graphics.TOP | Graphics.LEFT);
        }
    }

    /**
     * Dibuja la lista de perfiles de calidad con descripcion.
     * Cada perfil muestra su nombre y un rango de bitrates orientativo.
     */
    private void drawProfileList(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Perfil calidad", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Perfil calidad", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        // Descripciones cortas de cada perfil
        String[] descriptions = {
            "Mejor posible",
            "96-128 kbps",
            "48-64 kbps",
            "32 kbps"
        };

        int startY = 28;
        int itemH = fontSmall.getHeight() + 8;

        for (int i = 0; i < Settings.PROFILE_LABELS.length; i++) {
            int y = startY + i * itemH;

            if (i == subIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(4, y, w - 8, itemH - 2, 4, 4);
                g.setColor(colorAccent);
                g.drawLine(6, y + 1, w - 6, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(i == subIndex ? colorText : colorSoftkey);

            // Indicador de seleccion actual
            String prefix = (i == settings.qualityProfile) ? "\u2022 " : "  ";
            g.drawString(prefix + Settings.PROFILE_LABELS[i], 12, y + 2, Graphics.TOP | Graphics.LEFT);

            // Descripcion a la derecha
            g.setColor(colorAccent);
            g.drawString(descriptions[i], w - 12, y + 2, Graphics.TOP | Graphics.RIGHT);
        }
    }

    /** Menu de streaming */
    private void drawStreamingMenu(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Streaming", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Streaming", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        int startY = 28;
        int itemH = fontSmall.getHeight() + 8;

        String[] values = {
            Settings.PROFILE_LABELS[settings.qualityProfile],
            Settings.PRELOAD_LABELS[settings.preload],
            settings.showCovers ? "Si" : "No"
        };

        for (int i = 0; i < STREAMING_ITEMS.length; i++) {
            int y = startY + i * itemH;

            if (i == subIndex) {
                g.setColor(colorSelected);
                g.fillRoundRect(4, y, w - 8, itemH - 2, 4, 4);
                g.setColor(colorAccent);
                g.drawLine(6, y + 1, w - 6, y + 1);
            }

            g.setFont(fontSmall);
            g.setColor(i == subIndex ? colorText : colorSoftkey);
            g.drawString(STREAMING_ITEMS[i], 12, y + 2, Graphics.TOP | Graphics.LEFT);

            g.setColor(colorAccent);
            g.drawString(values[i], w - 12, y + 2, Graphics.TOP | Graphics.RIGHT);
        }
    }

    /** Pantalla de info de teclas */
    private void drawKeysInfo(Graphics g, int w, int h) {
        g.setFont(fontMedium);
        g.setColor(colorTextShadow);
        g.drawString("Teclas", w / 2 + 1, 3 + 1, Graphics.TOP | Graphics.HCENTER);
        g.setColor(colorText);
        g.drawString("Teclas", w / 2, 3, Graphics.TOP | Graphics.HCENTER);

        g.setFont(fontSmall);
        g.setColor(colorText);
        int y = 26;
        int lineH = fontSmall.getHeight() + 2;

        String[] lines = {
            "OK: Play/Pausa",
            "OK largo: Vel. x2",
            "\u2190: Retroceder 10s",
            "\u2192: Avanzar 10s",
            "\u2191: Cancion anterior",
            "\u2193: Siguiente cancion",
            "Laterales: Ant/Sig",
            "LSK: Opciones",
            "RSK: Biblioteca"
        };

        for (int i = 0; i < lines.length; i++) {
            g.setColor(i % 2 == 0 ? colorText : colorSoftkey);
            g.drawString(lines[i], 6, y + i * lineH, Graphics.TOP | Graphics.LEFT);
        }
    }

    // --- Controles ---

    protected void keyPressed(int keyCode) {
        int gameAction = 0;
        try {
            gameAction = getGameAction(keyCode);
        } catch (Exception e) {}

        if (keyCode == -6 || keyCode == -21) {
            // Atras
            goBack();
            repaint();
            return;
        }

        switch (state) {
            case STATE_MAIN:
                if (gameAction == Canvas.UP) {
                    menuIndex--;
                    if (menuIndex < 0) menuIndex = MENU_ITEMS.length - 1;
                } else if (gameAction == Canvas.DOWN) {
                    menuIndex++;
                    if (menuIndex >= MENU_ITEMS.length) menuIndex = 0;
                } else if (gameAction == Canvas.FIRE || keyCode == -7 || keyCode == -22) {
                    enterSubMenu();
                }
                break;

            case STATE_REPEAT:
                handleOptionSelect(gameAction, keyCode,
                                   Settings.REPEAT_LABELS.length);
                break;
            case STATE_SHUFFLE:
                handleOptionSelect(gameAction, keyCode, 2);
                break;
            case STATE_EQ:
                handleOptionSelect(gameAction, keyCode,
                                   Settings.EQ_LABELS.length);
                break;
            case STATE_THEME:
                handleOptionSelect(gameAction, keyCode,
                                   Settings.THEME_LABELS.length);
                break;
            case STATE_STREAMING:
                if (gameAction == Canvas.UP) {
                    subIndex--;
                    if (subIndex < 0) subIndex = STREAMING_ITEMS.length - 1;
                } else if (gameAction == Canvas.DOWN) {
                    subIndex++;
                    if (subIndex >= STREAMING_ITEMS.length) subIndex = 0;
                } else if (gameAction == Canvas.FIRE || keyCode == -7 || keyCode == -22) {
                    enterStreamingSub();
                }
                break;
            case STATE_QUALITY:
                handleOptionSelect(gameAction, keyCode,
                                   Settings.PROFILE_LABELS.length);
                break;
            case STATE_PRELOAD:
                handleOptionSelect(gameAction, keyCode,
                                   Settings.PRELOAD_LABELS.length);
                break;
            case STATE_COVERS:
                handleOptionSelect(gameAction, keyCode, 2);
                break;
            case STATE_KEYS:
                // Solo atras
                break;
        }

        repaint();
    }

    private void enterSubMenu() {
        subIndex = 0;
        switch (menuIndex) {
            case 0:
                state = STATE_REPEAT;
                subIndex = settings.repeat;
                break;
            case 1:
                state = STATE_SHUFFLE;
                subIndex = settings.shuffle ? 0 : 1;
                break;
            case 2:
                state = STATE_EQ;
                subIndex = settings.equalizer;
                break;
            case 3:
                state = STATE_THEME;
                subIndex = settings.theme;
                break;
            case 4:
                state = STATE_STREAMING;
                subIndex = 0;
                break;
            case 5:
                state = STATE_KEYS;
                break;
        }
    }

    private void enterStreamingSub() {
        switch (subIndex) {
            case 0:
                state = STATE_QUALITY;
                subIndex = settings.qualityProfile;
                break;
            case 1:
                state = STATE_PRELOAD;
                subIndex = settings.preload;
                break;
            case 2:
                state = STATE_COVERS;
                subIndex = settings.showCovers ? 0 : 1;
                break;
        }
    }

    private void handleOptionSelect(int gameAction, int keyCode, int count) {
        if (gameAction == Canvas.UP) {
            subIndex--;
            if (subIndex < 0) subIndex = count - 1;
        } else if (gameAction == Canvas.DOWN) {
            subIndex++;
            if (subIndex >= count) subIndex = 0;
        } else if (gameAction == Canvas.FIRE || keyCode == -7 || keyCode == -22) {
            applyOption();
        }
    }

    /** Aplica la opcion seleccionada */
    private void applyOption() {
        switch (state) {
            case STATE_REPEAT:
                settings.repeat = subIndex;
                break;
            case STATE_SHUFFLE:
                settings.shuffle = (subIndex == 0);
                break;
            case STATE_EQ:
                settings.equalizer = subIndex;
                break;
            case STATE_THEME:
                settings.theme = subIndex;
                applyTheme();
                midlet.onThemeChanged();
                break;
            case STATE_QUALITY:
                settings.qualityProfile = subIndex;
                break;
            case STATE_PRELOAD:
                settings.preload = subIndex;
                break;
            case STATE_COVERS:
                settings.showCovers = (subIndex == 0);
                break;
        }

        settings.save();
        goBack();
    }

    private void goBack() {
        switch (state) {
            case STATE_QUALITY:
            case STATE_PRELOAD:
            case STATE_COVERS:
                state = STATE_STREAMING;
                subIndex = 0;
                break;
            case STATE_MAIN:
                midlet.showPlayer();
                break;
            default:
                state = STATE_MAIN;
                break;
        }
    }

    // --- Utilidades de dibujo ---

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
}
