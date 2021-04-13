package jme3test.renderer;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.HttpZipLocator;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nonnull;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.CheckBox;
import de.lessvoid.nifty.controls.CheckBoxStateChangedEvent;
import de.lessvoid.nifty.controls.DropDown;
import de.lessvoid.nifty.controls.DropDownSelectionChangedEvent;
import de.lessvoid.nifty.controls.Slider;
import de.lessvoid.nifty.controls.SliderChangedEvent;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.render.RenderFont;

public class TestVideoOps extends SimpleApplication implements ScreenController {
    private static final String FULLSCREEN = "fullscreen";

    private Nifty nifty;

    /**
     * The maximum size of the monitor. This is used to stop cluttering up options with unused display values.
     * TODO - See how well this works on a multi-monitor setup with different monitor resolutions.
     */
    private int[] maxSize;
    /**
     * Resolutions to choose from.
     */
    private int[][] resolutions;
    /**
     * A selection of resolutions natively supported by the monitor.
     */
    private int[][] fullscreenResolutions;

    private FilterPostProcessor fpp;
    private ToneMapFilter toneMapFilter;
    private float whitePointLog = 1f;

    public static void main(String[] args) {
        AppSettings appSettings = new AppSettings(true);
        appSettings.setResizable(true);
        appSettings.setGammaCorrection(true);
//        appSettings.setSamples(8);
        TestVideoOps app = new TestVideoOps();
        app.setShowSettings(false);
        app.setSettings(appSettings);
        app.start();
    }

    public Geometry createHDRBox() {
        Box boxMesh = new Box(1, 1, 1);
        Geometry box = new Geometry("Box", boxMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", assetManager.loadTexture("Textures/HdrTest/Memorial.hdr"));
        box.setMaterial(mat);
        return box;
    }

    @Override
    public void simpleInitApp() {
        loadResolutions();
        // Obtain the resolutions available at fullscreen. This only works on LWJGL2
        loadFullscreenResolutions();

        NiftyJmeDisplay niftyDisplay = NiftyJmeDisplay.newNiftyJmeDisplay(assetManager, inputManager, audioRenderer, guiViewPort);
        nifty = niftyDisplay.getNifty();
        nifty.fromXml("Interface/Nifty/VideoSettings.xml", "start", this);
        guiViewPort.addProcessor(niftyDisplay);

        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setMoveSpeed(100);
        cam.setLocation(Vector3f.UNIT_Y.mult(5));

        fpp = new FilterPostProcessor(assetManager);
        toneMapFilter = new ToneMapFilter();
//        fpp.addFilter(toneMapFilter);
        viewPort.addProcessor(fpp);

        // We add light so we see the scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(1.3f));
        rootNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        rootNode.addLight(dl);

        assetManager.registerLocator("https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/jmonkeyengine/town.zip",
                HttpZipLocator.class);
        Spatial sceneModel = assetManager.loadModel("main.scene");
        sceneModel.setLocalScale(2f);

        rootNode.attachChild(sceneModel);
        sceneModel = createHDRBox();
        sceneModel.setLocalTranslation(3, 5, -5);
        rootNode.attachChild(sceneModel);

        inputManager.setCursorVisible(true);
        flyCam.setEnabled(false);
        flyCam.setDragToRotate(true);
    }

    /**
     * Loads up a list of possible resolutions, going up to 8K.
     */
    private void loadResolutions() {
        int i, j, l, m, skipped = 0;
        int[] res;
        int[] heights = new int[]{
                240, 256, 320, 360, 480, 576, 640, 720, 768, 900, 1024, 1080, 1200, 1280, 1440,
                1880, 2048, 2160, 4320};
        int[][] ratios = new int[][]{
                new int[]{4, 3}, new int[]{16, 9}, new int[]{16, 10}, new int[]{3, 2},
                new int[]{8, 5}, new int[]{1, 1}, new int[]{5, 4}, new int[]{2, 1}, new int[]{5, 3},
                new int[]{21, 9}, new int[]{25, 16}, new int[]{9, 5}, new int[]{7, 5},
                new int[]{4, 5}, new int[]{15, 4}, new int[]{14, 9}, new int[]{13, 11}
        };
        try {
            if (Class.forName("org.lwjgl.opengl.DisplayMode") != null) {
                // TODO - Uncomment on LWJGL 2
                maxSize = new int[2];
                maxSize[0] = org.lwjgl.opengl.Display.getDesktopDisplayMode().getWidth();
                maxSize[1] = org.lwjgl.opengl.Display.getDesktopDisplayMode().getHeight();
            }
        } catch (ClassNotFoundException e) {
            try {
                if (Class.forName("org.lwjgl.glfw.GLFW") != null) {
                    // TODO - Uncomment on LWJGL 3
//                    maxSize = Optional.of(org.lwjgl.glfw.GLFW.glfwGetVideoMode(org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor())).map(mode -> new int[]{mode.width(), mode.height()}).get();
                }
            } catch (ClassNotFoundException e1) {

            }
        }
        resolutions = new int[heights.length * ratios.length][2];
        for (i = 0, l = heights.length; i < l; i++) {
            for (j = 0, m = ratios.length; j < m; j++) {
                res = resolutions[i * m + j - skipped];
                res[1] = heights[i];
                res[0] = heights[i] * ratios[j][0] / ratios[j][1];
                // Only accept resolutions that can actually fit on the monitor
                if (res[0] < settings.getMinWidth() || res[0] > maxSize[0] || res[1] < settings.getMinHeight() || res[1] > maxSize[1]) {
                    res[0] = 0;
                    res[1] = 0;
                    skipped++;
                }
            }
        }
        // Put all blank entries at the bottom
        Arrays.sort(resolutions, (a, b) -> a[0] != b[0] ? (a[0] == 0 ? 1 : (b[0] == 0 ? -1 : (a[0] - b[0]))) : a[1] - b[1]);
    }

    /**
     * Loads up a list of resolutions the monitor supports. Note that on LWJGL 3, the only supported
     * resolution is the maximum resolution; everything else results in display bugs.
     */
    private void loadFullscreenResolutions() {
        try {
            if (Class.forName("org.lwjgl.opengl.DisplayMode") != null) {
                // TODO - Uncomment on LWJGL 2
                try {
                    fullscreenResolutions = Arrays.stream(org.lwjgl.opengl.Display.getAvailableDisplayModes()).map(mode -> new int[]{mode.getWidth(), mode.getHeight()}).toArray(int[][]::new);
                } catch (org.lwjgl.LWJGLException e) {
                    e.printStackTrace();
                    fullscreenResolutions = new int[0][2];
                }
            }
        } catch (ClassNotFoundException e) {
            // Only the full resolution is supported on LWJGL 3
            fullscreenResolutions = new int[][]{maxSize};
        }
        Arrays.sort(fullscreenResolutions, (a, b) -> a[0] != b[0] ? (a[0] == 0 ? 1 : (b[0] == 0 ? -1 : (a[0] - b[0]))) : a[1] - b[1]);
    }

    /**
     * Recursive function to return gcd of a and b
     *
     * @author JavaTPoint (https://www.javatpoint.com/java-program-to-find-gcd-of-two-numbers)
     */
    static int findGCD(int a, int b) {
        if (b == 0)
            return a;
        return findGCD(b, a % b);
    }

    @Override
    public void bind(@Nonnull Nifty nifty, @Nonnull Screen screen) {
        CheckBox fullscreen;
        DropDown resolutions;
        DropDown antialias;
        DropDown vsync;
        Slider fov;
        Slider hdr;
        switch (screen.getScreenId()) {
            case "start":
                fullscreen = this.nifty.getCurrentScreen().findNiftyControl("fullscreen", CheckBox.class);
                resolutions = this.nifty.getCurrentScreen().findNiftyControl("resolutions", DropDown.class);
                resolutions.setViewConverter(new DropDown.DropDownViewConverter() {
                    @Override
                    public void display(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        int[] arr;
                        int gcd;
                        if (item instanceof int[]) {
                            arr = (int[]) item;
                            gcd = findGCD(arr[0], arr[1]);
                            renderer.setText(arr[0] + " x " + arr[1] + " (" + arr[0] / gcd + " x " + arr[1] / gcd + ")");
                        }
                    }

                    @Override
                    public int getWidth(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        RenderFont font = renderer.getFont();
                        if (font == null) {
                            return 0;
                        }
                        return font.getWidth("aaaa x bbbb");
                    }
                });
                antialias = this.nifty.getCurrentScreen().findNiftyControl("antialias", DropDown.class);
                antialias.setViewConverter(new DropDown.DropDownViewConverter() {
                    @Override
                    public void display(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        int level;
                        if (item instanceof Integer) {
                            level = (Integer) item;
                            if (level == 0) {
                                renderer.setText("None");
                            } else {
                                renderer.setText(level + "x");
                            }
                        }
                    }

                    @Override
                    public int getWidth(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        RenderFont font = renderer.getFont();
                        if (font == null) {
                            return 0;
                        }
                        return font.getWidth("None");
                    }
                });
                vsync = this.nifty.getCurrentScreen().findNiftyControl("vsync", DropDown.class);
                vsync.setViewConverter(new DropDown.DropDownViewConverter() {
                    @Override
                    public void display(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        int level;
                        if (item instanceof Integer) {
                            level = (Integer) item;
                            if (level == 0) {
                                renderer.setText("Vsync");
                            } else if (level == -1) {
                                renderer.setText("Uncapped");
                            } else {
                                renderer.setText(level + " FPS");
                            }
                        }
                    }

                    @Override
                    public int getWidth(@Nonnull Element itemElement, @Nonnull Object item) {
                        TextRenderer renderer = itemElement.getRenderer(TextRenderer.class);
                        RenderFont font = renderer.getFont();
                        if (font == null) {
                            return 0;
                        }
                        return font.getWidth("Uncapped");
                    }
                });
                antialias.clear();
                antialias.addItem(0);
                for (int i = 2; i <= 16; i *= 2) {
                    antialias.addItem(i);
                }
                vsync.clear();
                vsync.addItem(-1); // Uncapped, no vsync
                vsync.addItem(0); // Vsync
                vsync.addItem(30); // Capped at 30
                vsync.addItem(60);
                vsync.addItem(90);
                vsync.addItem(120);
                vsync.addItem(144);
                vsync.addItem(160);
                vsync.addItem(165);
                vsync.addItem(170);
                vsync.addItem(240);
                vsync.addItem(360);

                fov = this.nifty.getCurrentScreen().findNiftyControl("fov", Slider.class);
                hdr = this.nifty.getCurrentScreen().findNiftyControl("hdr", Slider.class);

                fullscreen.setChecked(settings.isFullscreen());
                resolutions.selectItem(new int[]{settings.getWidth(), settings.getHeight()});
                antialias.selectItem(settings.getSamples());
                if (settings.isVSync()) {
                    vsync.selectItem(0);
                } else {
                    vsync.selectItem(settings.getFrameRate());
                }
                fov.setValue(this.cam.getFov());
                this.nifty.getCurrentScreen().findElementById("fovIndicator").getRenderer(TextRenderer.class).setText((int) fov.getValue() + "\u00b0");
                this.resetResolutionsDropdown();
                break;
        }
    }

    @NiftyEventSubscriber(id = "fullscreen")
    public void onFullscreenChange(final String id, final CheckBoxStateChangedEvent event) {
        CheckBox borderless = this.nifty.getCurrentScreen().findNiftyControl("borderless", CheckBox.class);
        this.resetResolutionsDropdown();
        borderless.setEnabled(!event.isChecked());
    }

    @NiftyEventSubscriber(id = "resolutions")
    public void onResolutionChange(final String id, final DropDownSelectionChangedEvent event) {
        this.resetModeParameters(this.nifty.getCurrentScreen().findNiftyControl("fullscreen", CheckBox.class).isChecked(), (int[]) event.getSelection());
    }

    @NiftyEventSubscriber(id = "fov")
    public void onFovUpdate(final String id, final SliderChangedEvent event) {
        this.cam.setFov(event.getValue());
        this.nifty.getCurrentScreen().findElementById("fovIndicator").getRenderer(TextRenderer.class).setText((int) event.getValue() + "\u00b0");
    }

    @NiftyEventSubscriber(id = "hdr")
    public void onHdrUpdate(final String id, final SliderChangedEvent event) {
        if (event.getValue() == -1) {
            if (fpp.getFilter(ToneMapFilter.class) != null) {
                fpp.removeFilter(toneMapFilter);
            }
            this.nifty.getCurrentScreen().findElementById("hdrIndicator").getRenderer(TextRenderer.class).setText("Off");
        } else {
            if (fpp.getFilter(ToneMapFilter.class) == null) {
                fpp.addFilter(toneMapFilter);
            }
            float wp = FastMath.exp(event.getValue());
            toneMapFilter.setWhitePoint(new Vector3f(wp, wp, wp));
            this.nifty.getCurrentScreen().findElementById("hdrIndicator").getRenderer(TextRenderer.class).setText(Float.toString(wp));
        }
    }

    /**
     * Updates the available color depths and refresh rates.
     *
     * @param fullscreen
     * @param selection
     */
    private void resetModeParameters(boolean fullscreen, int[] selection) {
        int depth, frequency;
        AppSettings settings = this.settings;
        DropDown depths = this.nifty.getCurrentScreen().findNiftyControl("bitDepths", DropDown.class);
        DropDown refreshRates = this.nifty.getCurrentScreen().findNiftyControl("refreshRates", DropDown.class);
        depths.clear();
        refreshRates.clear();

        try {
            if (Class.forName("org.lwjgl.opengl.DisplayMode") != null) {
                // TODO - Uncomment on LWJGL2
                try {
                    org.lwjgl.opengl.DisplayMode[] modes = org.lwjgl.opengl.Display.getAvailableDisplayModes();
                    Arrays.sort(modes, (mode1, mode2) -> mode1.getWidth() == selection[0] && mode1.getHeight() == selection[1] && mode2.getWidth() != selection[0] && mode2.getHeight() != selection[1] ? -1 : (mode1.getWidth() != selection[0] && mode1.getHeight() != selection[1] && mode2.getWidth() == selection[0] && mode2.getHeight() == selection[1] ? 1 : 0));
                    for (org.lwjgl.opengl.DisplayMode mode : modes) {
                        if (mode.getWidth() != selection[0] || mode.getHeight() != selection[1]) {
                            frequency = mode.getFrequency();
                            if (frequency == 59) {
                                frequency = 60;
                            }
                            if (mode.getBitsPerPixel() >= 16 || mode.getBitsPerPixel() <= 0 && !depths.getItems().contains(mode.getBitsPerPixel())) {
                                depths.addItem(mode.getBitsPerPixel());
                                if (settings.getBitsPerPixel() == mode.getBitsPerPixel()) {
                                    depths.selectItemByIndex(depths.itemCount() - 1);
                                }
                            }
                            if (!refreshRates.getItems().contains(frequency)) {
                                refreshRates.addItem(frequency);
                                if (settings.getFrequency() == frequency) {
                                    refreshRates.selectItemByIndex(refreshRates.itemCount() - 1);
                                }
                            }
                            // We've gone through all the valid modes
                            break;
                        }
                    }
                } catch (org.lwjgl.LWJGLException e) {
                    e.printStackTrace();
                }
            }
        } catch (ClassNotFoundException e) {

        }
        if (depths.itemCount() == 0) {
            /*
             * The default
             */
            depths.addItem(16);
            depths.addItem(24);
        } else if (depths.itemCount() == 1 && depths.getItems().get(0).equals(-1)) {
            depths.clear();
            depths.addItem(24);
        }
        if (refreshRates.itemCount() == 0) {
            /*
             * The default
             */
            refreshRates.addItem(60);
        }
        if (depths.getSelectedIndex() == -1) {
            depths.selectItemByIndex(0);
        }
        if (refreshRates.getSelectedIndex() == -1) {
            refreshRates.selectItemByIndex(0);
        }
    }

    /**
     * Updates the available resolutions.
     */
    private void resetResolutionsDropdown() {
        AppSettings settings = this.settings;
        boolean isFullscreen = this.nifty.getCurrentScreen().findNiftyControl("fullscreen", CheckBox.class).isChecked();
        int[][] modes = isFullscreen && fullscreenResolutions.length > 0 ? fullscreenResolutions : resolutions;

        DropDown resolutions = this.nifty.getCurrentScreen().findNiftyControl("resolutions", DropDown.class);
        resolutions.clear();
        int[] lastMode = null;
        for (int[] mode : modes) {
            if (mode == null || mode[0] == 0) {
                // Blank
                continue;
            }
            if (lastMode == null || lastMode[0] != mode[0] || lastMode[1] != mode[1]) {
                resolutions.addItem(mode);
                if (settings.getWidth() == mode[0] && settings.getHeight() == mode[1]) {
                    resolutions.selectItemByIndex(resolutions.itemCount() - 1);
                    this.resetModeParameters(isFullscreen, (int[]) resolutions.getSelection());
                }
            }
            lastMode = mode;
        }
        if (resolutions.getSelectedIndex() == -1) {
            resolutions.selectItemByIndex(0);
            this.resetModeParameters(isFullscreen, (int[]) resolutions.getSelection());
        }
    }

    @Override
    public void onStartScreen() {

    }

    @Override
    public void onEndScreen() {

    }

    /**
     * Updates the display window itself.
     */
    private void applyFullscreen() {
        CheckBox fullscreen = this.nifty.getCurrentScreen().findNiftyControl("fullscreen", CheckBox.class);
        CheckBox borderless = this.nifty.getCurrentScreen().findNiftyControl("borderless", CheckBox.class);
        DropDown resolutions = this.nifty.getCurrentScreen().findNiftyControl("resolutions", DropDown.class);
        DropDown depths = this.nifty.getCurrentScreen().findNiftyControl("bitDepths", DropDown.class);
        DropDown refreshRates = this.nifty.getCurrentScreen().findNiftyControl("refreshRates", DropDown.class);
        this.settings.setFullscreen(fullscreen.isChecked());
        // Technically, "fullscreen" mode on LWJGL3 is actually just borderless windowed, not true fullscreen.
        if (!fullscreen.isChecked()) {
            System.setProperty("org.lwjgl.opengl.Window.undecorated", Boolean.toString(borderless.isChecked()));
        }
        this.settings.putBoolean("borderless", borderless.isChecked());
        this.settings.setResolution(((int[]) resolutions.getSelection())[0], ((int[]) resolutions.getSelection())[1]);
        this.settings.setBitsPerPixel((Integer) depths.getSelection());
        this.settings.setFrequency((Integer) refreshRates.getSelection());
    }

    /**
     * Callback for applying all settings.
     *
     * @param id
     * @param event
     */
    @NiftyEventSubscriber(id = "apply")
    public void apply(String id, final ButtonClickedEvent event) {
//        this.applyFullscreen();
//        DropDown antialias = this.nifty.getCurrentScreen().findNiftyControl("antialias", DropDown.class);
//        DropDown vsync = this.nifty.getCurrentScreen().findNiftyControl("vsync", DropDown.class);
//        this.settings.setSamples((Integer) antialias.getSelection());
//        switch ((Integer) vsync.getSelection()) {
//            case -1:
//                this.settings.setVSync(false);
//                this.settings.setFrameRate(-1);
//                break;
//            case 0:
//                this.settings.setVSync(true);
//                this.settings.setFrameRate(-1);
//                break;
//            default:
//                this.settings.setVSync(false);
//                this.settings.setFrameRate((Integer) vsync.getSelection());
//                break;
//        }
//        this.settings.setGammaCorrection(true);
        this.getContext().restart();
        /**
         * Reset the nifty renderer
         */
        this.nifty.gotoScreen("start");
        /**
         * I realize that this is "internal use only," but I can't figure out how else to update nifty.
         */
        this.renderManager.notifyReshape(this.settings.getWidth(), this.settings.getHeight());

//        this.settings.isGammaCorrection()
    }
}
