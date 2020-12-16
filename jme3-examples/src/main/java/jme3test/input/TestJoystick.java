package jme3test.input;

import com.jme3.app.SimpleApplication;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.Joystick;
import com.jme3.input.JoystickAxis;
import com.jme3.input.JoystickButton;
import com.jme3.input.JoystickConnectionListener;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays raw output from the joysticks connected.
 *
 * @author Markil 3
 * @author Normen Hansen
 * @author Kirill Vainer
 * @author Paul Speed
 * @author dokthar
 * @author Stephen Gold
 */
public class TestJoystick extends SimpleApplication {
    private static final Logger logger =
            Logger.getLogger(TestJoystick.class.getName());

    final ColorRGBA BUTTON_COLOR = new ColorRGBA(0F, 0.1F, 0F, 1F);
    final ColorRGBA HIGHLIGHTED_BUTTON_COLOR =
            new ColorRGBA(0F, 0.75F, 0.25F, 1F);

    private Joystick viewedJoystick;
    private float yInfo = 0;

    private BitmapFont guiFont;

    private Node[] gamepadCont;

    private GamepadView[] gamepadView;
    private Node[] gamepadHeaders;

    private BitmapText[][] labels;

    private BitmapText refLabel;

    private Map<JoystickAxis, Float> lastValues = new HashMap<>();

    public static void main(String[] args) {
        TestJoystick app = new TestJoystick();
        AppSettings settings = new AppSettings(true);
        settings.setUseJoysticks(true);
        app.setSettings(settings);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        getFlyByCamera().setEnabled(false);

        Joystick[] joysticks = inputManager.getJoysticks();
        if (joysticks == null) {
            throw new IllegalStateException("Cannot find any joysticks!");
        }

        try {
            PrintWriter out = new PrintWriter(new FileWriter(
                    "joysticks-" + System.currentTimeMillis() + ".txt"));
            dumpJoysticks(joysticks, out);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Error writing joystick dump", e);
        }

        this.guiFont =
                this.assetManager.loadFont("Interface/Fonts/Default.fnt");
        this.refLabel = this.guiFont.createLabel("Axis X/Axis Y");
        this.guiNode.attachChild(this.refLabel);

        this.updateGamepad();

        this.resize(this.getCamera().getWidth(), this.getCamera().getHeight());

        // Add a raw listener because it's easier to get all joystick events
        // this way.
        inputManager.addRawInputListener(new JoystickEventListener());
        inputManager.addJoystickConnectionListener(new JoystickConnListener());

        // add action listener for mouse click
        // to all easier custom mapping
        inputManager.addMapping("mouseClick",
                new MouseButtonTrigger(mouseInput.BUTTON_LEFT));
        inputManager.addListener(new ActionListener() {
            @Override
            public void onAction(String name, boolean isPressed, float tpf) {
                Integer gamepad;
                if (isPressed) {
                    pickGamePad(getInputManager().getCursorPosition());

                    CollisionResults results = pick(getCamera(), getInputManager().getCursorPosition(), guiNode);
                    if (results.size() > 0) {
                        for (CollisionResult result : results) {
                            if (result.getGeometry().getUserData("gamepad") != null) {
                                gamepad = result.getGeometry().getUserData("gamepad");
                                for (int i = 0, l = gamepadCont.length; i < l; i++) {
                                    if (i == gamepad) {
                                        guiNode.attachChild(gamepadCont[i]);
                                        ((Geometry) gamepadHeaders[i].getChild(0))
                                                .getMaterial().setColor("Color",
                                                HIGHLIGHTED_BUTTON_COLOR);
                                    } else {
                                        gamepadCont[i].removeFromParent();
                                        ((Geometry) gamepadHeaders[i].getChild(0))
                                                .getMaterial()
                                                .setColor("Color", BUTTON_COLOR);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }, "mouseClick");
    }

    protected void dumpJoysticks(Joystick[] joysticks, PrintWriter out) {
        for (Joystick j : joysticks) {
            out.println("Joystick[" + j.getJoyId() + "]:" + j.getName());
            out.println("  buttons:" + j.getButtonCount());
            for (JoystickButton b : j.getButtons()) {
                out.println("   " + b);
            }

            out.println("  axes:" + j.getAxisCount());
            for (JoystickAxis axis : j.getAxes()) {
                out.println("   " + axis);
            }
        }
    }

    /**
     * Adds all the GUI elements to the screen.
     *
     * @author Markil 3
     * @since 3.4
     */
    private void updateGamepad() {
        int mostButtons = 0;
        Joystick joy;
        int l = this.getInputManager().getJoysticks().length;

        /*
         * Removes any existing gamepads.
         */
        if (this.gamepadCont != null) {
            for (Node cont : this.gamepadCont) {
                if (cont != null) {
                    cont.removeFromParent();
                }
            }
        }

        /*
         * Removes the labels, just in case something changed and the
         * previous ones are incompatible.
         */
        if (this.labels != null) {
            for (BitmapText[] cont : this.labels) {
                for (BitmapText cont2 : cont) {
                    if (cont2 != null) {
                        cont2.removeFromParent();
                    }
                }
            }
        }
        /*
         * Initialize the array of labels and gamepads.
         */
        for (int i = 0; i < l; i++) {
            joy = this.getInputManager().getJoysticks()[i];
            mostButtons = Math.max(joy.getAxisCount() + joy.getButtonCount(),
                    mostButtons);
        }
        this.labels = new BitmapText[l][mostButtons * 2 + 3];
        this.gamepadCont =
                new Node[this.getInputManager().getJoysticks().length];
        this.gamepadView =
                new GamepadView[this.getInputManager().getJoysticks().length];

        for (int i = 0; i < l; i++) {
            this.gamepadCont[i] = new Node();
            this.gamepadView[i] = new GamepadView();
            this.gamepadView[i].setLocalTranslation(-128, -384, 0);
            this.gamepadCont[i].attachChild(this.gamepadView[i]);
            if (i == 0) {
                this.guiNode.attachChild(this.gamepadCont[i]);
            }
        }

        this.addButtons();
    }

    /**
     * Adds the tab buttons used to switch between which gamepad is currently
     * being viewed.
     *
     * @author Markil 3
     * @since 3.4
     */
    private void addButtons() {
        Node button;
        BitmapText buttonText;
        Geometry buttonBackground;
        int i, l;

        /*
         * Clears out any old tab buttons.
         */
        if (this.gamepadHeaders != null) {
            for (i = 0, l = this.gamepadHeaders.length; i < l; i++) {
                this.gamepadHeaders[i].removeFromParent();
            }
        }

        l = this.gamepadCont.length;
        this.gamepadHeaders = new Node[l];
        for (i = 0, l = this.gamepadCont.length; i < l; i++) {
            button = new Node();
            buttonText = this.guiFont.createLabel("Gamepad " +
                    this.getInputManager().getJoysticks()[i].getJoyId());
            buttonText.setUserData("gamepad", i);
            buttonBackground = new Geometry("gamepad" + i,
                    new Quad(buttonText.getLineWidth() + 10,
                            buttonText.getLineHeight() + 5));
            buttonBackground.setMaterial(new Material(this.getAssetManager(),
                    "Common/MatDefs/Misc/Unshaded.j3md"));
            if (this.gamepadCont[i].getParent() != null) {
                buttonBackground.getMaterial()
                        .setColor("Color", HIGHLIGHTED_BUTTON_COLOR);
            } else {
                buttonBackground.getMaterial().setColor("Color", BUTTON_COLOR);
            }
            buttonBackground.setUserData("gamepad", i);
            buttonBackground
                    .setLocalTranslation(0, -buttonText.getLineHeight(), 0);
            button.attachChild(buttonBackground);
            button.attachChild(buttonText);
            this.guiNode.attachChild(button);
            this.gamepadHeaders[i] = button;
        }
    }

    /**
     * Updates the values of the gamepad labels as buttons and axis are
     * manipulated.
     *
     * @param joy - The gamepad to update.
     * @author Markil 3
     * @since 3.4
     */
    private void setLabels(Joystick joy) {
        /*
         * Removes the old labels.
         */
        if (labels != null) {
            for (BitmapText label : this.labels[joy.getJoyId()]) {
                if (label != null) {
                    label.removeFromParent();
                }
            }
        }
        //		this.labels = new Label[joy.getJoyId()][(joy.getAxisCount() +
        //		joy.getButtonCount()) * 2 + 1];
        if (this.labels != null) {
            /*
             * The name of the gamepad.
             */
            this.labels[joy.getJoyId()][0] =
                    this.guiFont.createLabel(joy.getName());
            this.labels[joy.getJoyId()][0].setLocalTranslation(20, getCamera().getHeight() - 25, 0);
            this.gamepadCont[joy.getJoyId()]
                    .attachChild(this.labels[joy.getJoyId()][0]);
            /*
             * The key for the axis. Each of the axis rows will display the
             * index of the axis, its given name, its logical ID after
             * joystick remapping has occurred, and the axis index again.
             */
            this.labels[joy.getJoyId()][1] = this.guiFont
                    .createLabel("Axis Index: Axis Name (logical ID, axis ID)");
            this.labels[joy.getJoyId()][1].setLocalTranslation(20, getCamera().getHeight() - 50, 0);
            this.gamepadCont[joy.getJoyId()]
                    .attachChild(this.labels[joy.getJoyId()][1]);
            /*
             * Loop through all the axes.
             */
            for (int i = 0; i < joy.getAxisCount(); i++) {
                JoystickAxis axis = joy.getAxes().get(i);
                try {
                    /*
                     * The name and information of the axis.
                     */
                    BitmapText label = this.guiFont.createLabel(
                            (i) + ": " + axis.getName() + " (" +
                                    axis.getLogicalId() + ", " +
                                    axis.getAxisId() + "): ");
                    label.setLocalTranslation(20, getCamera().getHeight() - 25 * (i + 3), 0);
                    this.labels[joy.getJoyId()][i * 2 + 1] = label;

                    /*
                     * The current value of the axis.
                     */
                    BitmapText label2 = this.guiFont.createLabel("-1.0");
                    label2.setLocalTranslation(label.getLocalTranslation()
                            .add(label.getLineWidth(), 0, 0));
                    this.labels[joy.getJoyId()][i * 2 + 2] = label2;

                    this.gamepadCont[joy.getJoyId()].attachChild(label);
                    this.gamepadCont[joy.getJoyId()].attachChild(label2);
                } catch (ArrayIndexOutOfBoundsException aie) {
                    logger.log(Level.SEVERE,
                            "Couldn't find index for axis (" + axis.getName() +
                                    ", " + axis.getLogicalId() + ", " +
                                    axis.getAxisId() + ")", aie);
//                    throw new RuntimeException(
//                            "Couldn't find index for button " + i + " (" +
//                            axis.getName() + ", " +
//                            axis.getLogicalId() + ", " +
//                            axis.getAxisId() + ")", aie);
                }
            }

            int firstButtonIndex = 2 * joy.getAxisCount() + 1;
            /*
             * The key for the buttons. Each of the button rows will display the
             * index of the button offset by the number of axes we had, its
             * given name, its logical ID after
             * joystick remapping has occurred, and the actual button index.
             */
            this.labels[joy.getJoyId()][firstButtonIndex] = this.guiFont
                    .createLabel(
                            "Button Index: Button Name (logical ID, button " +
                                    "ID)");
            this.labels[joy.getJoyId()][firstButtonIndex].setLocalTranslation(
                    this.getCamera().getWidth() -
                            this.labels[joy.getJoyId()][firstButtonIndex]
                                    .getLineWidth(), getCamera().getHeight() - 50, 0);
            this.gamepadCont[joy.getJoyId()]
                    .attachChild(this.labels[joy.getJoyId()][firstButtonIndex]);

            /*
             * Loop through all the buttons.
             */
            for (int i = 0; i < joy.getButtonCount(); i++) {
                JoystickButton button = joy.getButtons().get(i);
                try {
                    /*
                     * The current value of the button. We create this first
                     since the name will be placed in relation to this, due
                     to it being right-justified.
                     */
                    BitmapText label2 = this.guiFont.createLabel("false");
                    label2.setLocalTranslation(
                            this.getCamera().getWidth() - label2.getLineWidth(),
                            getCamera().getHeight() - 25 * (i + 3), 0);

                    /*
                     * The name and information for the button.
                     */
                    BitmapText label = this.guiFont.createLabel(
                            (i + joy.getAxisCount()) + ": " + button.getName() +
                                    " (" + button.getLogicalId() + ", " +
                                    button.getButtonId() + "): ");
                    label.setLocalTranslation(label2.getLocalTranslation()
                            .add(-label.getLineWidth(), 0, 0));

                    this.labels[joy.getJoyId()][2 * (i + joy.getAxisCount()) +
                            2] = label;
                    this.labels[joy.getJoyId()][2 * (i + joy.getAxisCount()) +
                            3] = label2;

                    this.gamepadCont[joy.getJoyId()].attachChild(label);
                    this.gamepadCont[joy.getJoyId()].attachChild(label2);
                } catch (ArrayIndexOutOfBoundsException aie) {
                    logger.log(Level.SEVERE,
                            "Couldn't find index for button (" +
                                    button.getName() + ", " +
                                    button.getLogicalId() + ", " +
                                    button.getButtonId() + ")", aie);
//                    throw new RuntimeException(
//                            "Couldn't find index for button " + i + " (" +
//                            button.getName() + ", " +
//                            button.getLogicalId() + ", " +
//                            button.getButtonId() + ")", aie);
                }
            }
        }
    }

    /**
     * Repositions the screen elements.
     *
     * @param width  - The width of the screen.
     * @param height - The height of the screen.
     * @author Markil 3
     * @since 3.4
     */
    public void resize(int width, int height) {
        if (this.gamepadHeaders != null) {
            Node button;
            for (int i = 0, l = this.gamepadHeaders.length; i < l; i++) {
                button = this.gamepadHeaders[i];
                button.setLocalTranslation(128 * i, height, 0);
            }
        }
        if (this.gamepadView != null) {
            for (GamepadView view : this.gamepadView) {
                view.setLocalTranslation(width / 2F - 256F, height - 512F, 0);
            }
        }
        this.refLabel.setLocalTranslation(
                (width - this.refLabel.getLineWidth()) / 2F, 0, 0);
    }

    /**
     * Easier to watch for all button and axis events with a raw input
     * listener.
     */
    protected class JoystickEventListener implements RawInputListener {

        private Map<JoystickAxis, Float> lastValues = new HashMap<>();

        /**
         * Updates the values of the joystick button labels.
         *
         * @param evt - The input event data.
         * @author Markil 3
         * @since 3.4
         */
        @Override
        public void onJoyAxisEvent(JoyAxisEvent evt) {
            //		setViewedJoystick(evt.getAxis().getJoystick());
//        this.gamepadView[evt.getJoyIndex()]
//                .setAxisValue(evt.getAxis(), evt.getValue());
            Float last = this.lastValues.remove(evt.getAxis());
            float value = evt.getValue();

            // Check the axis dead zone.  InputManager normally does this
            // by default but not for raw events like we get here.
            float effectiveDeadZone =
                    Math.max(inputManager.getAxisDeadZone(), evt.
                            getAxis().getDeadZone());
            if (Math.abs(value) < effectiveDeadZone) {
                if (last == null) {
                    // Just skip the event
                    return;
                }
                // Else set the value to 0
                lastValues.remove(evt.getAxis());
                value = 0;
            }
            gamepadView[evt.getJoyIndex()].setAxisValue(evt.getAxis(), value);
            if (value != 0) {
                lastValues.put(evt.getAxis(), value);
            }
        }

        /**
         * Updates the values of the joystick button labels.
         *
         * @param evt - The input event data.
         * @author Markil 3
         * @since 3.4
         */
        @Override
        public void onJoyButtonEvent(JoyButtonEvent evt) {
            //		setViewedJoystick(evt.getButton().getJoystick());
            gamepadView[evt.getJoyIndex()]
                    .setButtonValue(evt.getButton(), evt.isPressed());
        }

        public void beginInput() {
        }

        public void endInput() {
        }

        public void onMouseMotionEvent(MouseMotionEvent evt) {
        }

        public void onMouseButtonEvent(MouseButtonEvent evt) {
        }

        public void onKeyEvent(KeyInputEvent evt) {
        }

        public void onTouchEvent(TouchEvent evt) {
        }
    }

    /**
     * Listens for joysticks connecting and disconnecting.
     *
     * @author Markil 3
     * @since 3.4
     */
    protected class JoystickConnListener implements JoystickConnectionListener {
        /*
         * TODO - These don't seem to trigger on jme3-lwjgl. Someone probably forgot to implement
         *  the callbacks or something.
         */
        @Override
        public void onConnected(Joystick joystick) {
            updateGamepad();
            resize(getCamera().getWidth(), getCamera().getHeight());
        }

        @Override
        public void onDisconnected(Joystick joystick) {
            /*
             * TODO - This callback will fire before the joystick is actually
             *  removed. This is helpful at times, but it does mean we will
             * have a
             *  blank slot because this code still thinks we have the one
             * that was
             *  removed.
             */
            updateGamepad();
            resize(getCamera().getWidth(), getCamera().getHeight());
        }
    }

    /**
     * This node serves as the center of logic for each gamepad connected to
     * the computer.
     */
    private class GamepadView extends Node {
        float xAxis = 0;
        float yAxis = 0;
        float zAxis = 0;
        float zRotation = 0;

        float lastPovX = 0;
        float lastPovY = 0;

        float leftTrig = -1F;
        float rightTrig = -1F;

        Geometry leftStick;
        Geometry rightStick;

        Map<String, ButtonView> buttons = new HashMap<>();

        private boolean l2;

        private boolean r2;

        GamepadView() {
            super("gamepad");

            // Sizes naturally for the texture size.  All positions will
            // be in that space because it's easier.
            int size = 512;

            Material m = new Material(assetManager,
                    "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", assetManager
                    .loadTexture("Interface/Joystick/gamepad-buttons.png"));
            m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            Geometry buttonPanel =
                    new Geometry("buttons", new Quad(size, size));
            buttonPanel.setLocalTranslation(0, 0, -1);
            buttonPanel.setMaterial(m);
            attachChild(buttonPanel);

            m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", assetManager
                    .loadTexture("Interface/Joystick/gamepad-frame.png"));
            m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            Geometry frame = new Geometry("frame", new Quad(size, size));
            frame.setMaterial(m);
            attachChild(frame);

            m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap", assetManager
                    .loadTexture("Interface/Joystick/gamepad-stick.png"));
            m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            leftStick = new Geometry("leftStick", new Quad(64, 64));
            leftStick.setMaterial(m);
            attachChild(leftStick);
            rightStick = new Geometry("rightStick", new Quad(64, 64));
            rightStick.setMaterial(m);
            attachChild(rightStick);

            // A "standard" mapping... fits a majority of my game pads
            addButton(JoystickButton.BUTTON_0, 371, 512 - 176, 42, 42); // ACTION_TOP
            addButton(JoystickButton.BUTTON_1, 407, 512 - 212, 42, 42); // ACTION_RIGHT
            addButton(JoystickButton.BUTTON_2, 371, 512 - 248, 42, 42); // ACTION_BOTTOM
            addButton(JoystickButton.BUTTON_3, 334, 512 - 212, 42, 42); // ACTION_LEFT

            // Front buttons  Some of these have the top ones and the bottoms
            // ones flipped.
            addButton(JoystickButton.BUTTON_4, 67, 512 - 111, 95, 21); // L1
            addButton(JoystickButton.BUTTON_5, 348, 512 - 111, 95, 21); // R1
            addButton(JoystickButton.BUTTON_6, 67, 512 - 89, 95, 21); // L2
            addButton(JoystickButton.BUTTON_7, 348, 512 - 89, 95, 21); // R2

            // Select and start buttons
            addButton(JoystickButton.BUTTON_8, 206, 512 - 198, 48, 30); // SELECT
            addButton(JoystickButton.BUTTON_9, 262, 512 - 198, 48, 30); // START

            // Joystick push buttons
            addButton(JoystickButton.BUTTON_10, 147, 512 - 300, 75, 70); // L3
            addButton(JoystickButton.BUTTON_11, 285, 512 - 300, 75, 70); // R3

            //    +Y
            //  -X  +X
            //    -Y
            //
            addButton("14", 96, 512 - 174, 40, 38); // DPAD_UP
            addButton("13", 128, 512 - 208, 40, 38); // DPAD_RIGHT
            addButton("15", 96, 512 - 239, 40, 38); // DPAD_DOWN
            addButton("12", 65, 512 - 208, 40, 38); // DPAD_LEFT

            resetPositions();
        }

        private void addButton(String name, float x, float y, float width,
                               float height) {
            ButtonView b = new ButtonView(name, x, y, width, height);
            attachChild(b);
            buttons.put(name, b);
        }

        void setAxisValue(JoystickAxis axis, float value) {
            Logger.getLogger(axis.getJoystick().getName())
                    .info(axis.getJoystick().getName() + "\n\tAxis:" +
                            axis.getName() + " (" + axis.getAxisId() + ")=" +
                            value);
            if (labels == null ||
                    labels[axis.getJoystick().getJoyId()][0] == null ||
                    !labels[axis.getJoystick().getJoyId()][0].getText()
                            .equals(axis.getJoystick().getName())) {
                setLabels(axis.getJoystick());
            }
            if (axis == axis.getJoystick().getXAxis()) {
                setXAxis(value);
            } else if (axis == axis.getJoystick().getYAxis()) {
                setYAxis(-value);
            } else if (axis == axis.getJoystick().getAxis(JoystickAxis.Z_AXIS)) {
                // Note: in the above condition, we could check the axis name
                // but
                //       I have at least one joystick that reports 2 "Z Axis"
                //       axes.
                //       In this particular case, the first one is the right
                //       one so
                //       a name based lookup will find the proper one.  It's
                //       a problem
                //       because the erroneous axis sends a constant stream
                //       of values.
                setZAxis(value);
            } else if (axis ==
                    axis.getJoystick().getAxis(JoystickAxis.Z_ROTATION)) {
                setZRotation(-value);
            } else if (axis ==
                    axis.getJoystick().getAxis(JoystickAxis.LEFT_TRIGGER)) {
                if (axis.getJoystick().getButton(JoystickButton.BUTTON_6) == null) {
                    // left/right triggers sometimes only show up as axes
                    boolean pressed = value > 0;
                    if (pressed != this.buttons.get(JoystickButton.BUTTON_6).
                            isDown()) {
                        setButtonValue(JoystickButton.BUTTON_6, pressed); // L2
                    }
                }
            } else if (axis ==
                    axis.getJoystick().getAxis(JoystickAxis.RIGHT_TRIGGER)) {
                if (axis.getJoystick().getButton(JoystickButton.BUTTON_7) == null) {
                    // left/right triggers sometimes only show up as axes
                    boolean pressed = value > 0;
                    if (pressed != this.buttons.get(JoystickButton.BUTTON_7).
                            isDown()) {
                        setButtonValue(JoystickButton.BUTTON_7, pressed); // R2
                    }
                }
            } else if (axis == axis.getJoystick().getPovXAxis()) {
                if (lastPovX < 0) {
                    setButtonValue("12", false); // DPAD_LEFT
                } else if (lastPovX > 0) {
                    setButtonValue("13", false); // DPAD_RIGHT
                }
                if (value < 0) {
                    setButtonValue("12", true); // DPAD_LEFT
                } else if (value > 0) {
                    setButtonValue("13", true); // DPAD_RIGHT
                }
                lastPovX = value;
            } else if (axis == axis.getJoystick().getPovYAxis()) {
                if (lastPovY < 0) {
                    setButtonValue("15", false); // DPAD_DOWN
                } else if (lastPovY > 0) {
                    setButtonValue("14", false); // DPAD_UP
                }
                if (value < 0) {
                    setButtonValue("15", true); // DPAD_DOWN
                } else if (value > 0) {
                    setButtonValue("14", true); // DPAD_UP
                }
                lastPovY = value;
            }
            labels[axis.getJoystick().getJoyId()][axis.
                    getAxisId() * 2 + 2].setText(Float.toString(value));
        }

        void setButtonValue(JoystickButton button, boolean isPressed) {
            Logger.getLogger(button.getJoystick().getName())
                    .info(button.getJoystick().getName() + "\n\tButton:" +
                            button.getName() + " (" + button.getButtonId() +
                            ")=" + (isPressed ? "Down" : "Up"));
            if (labels == null ||
                    labels[button.getJoystick().getJoyId()][0] == null ||
                    !labels[button.getJoystick().getJoyId()][0].getText()
                            .equals(button.getJoystick().getName())) {
                setLabels(button.getJoystick());
            }
            if (button.getLogicalId().equals(JoystickButton.BUTTON_6)) {
                l2 = isPressed;
                setButtonValue(button.getLogicalId(),
                        isPressed || this.leftTrig >= 0F);
            } else if (button.getLogicalId().equals(JoystickButton.BUTTON_7)) {
                r2 = isPressed;
                setButtonValue(button.getLogicalId(),
                        isPressed || this.rightTrig >= 0F);
            } else {
                setButtonValue(button.getLogicalId(), isPressed);
            }
            try {
                labels[button.getJoystick().getJoyId()][2 *
                        (button.getButtonId() +
                                button.getJoystick().getAxisCount()) + 3]
                        .setText(Boolean.toString(isPressed));
            } catch (ArrayIndexOutOfBoundsException aie) {
                logger.log(Level.SEVERE,
                        "Couldn't find index for button (" + button.getName() +
                                ", " + button.getLogicalId() + ", " +
                                button.getButtonId() + ")", aie);
//                    throw new RuntimeException(
//                            "Couldn't find index for button (" +
//                            button.getName() + ", " +
//                            button.getLogicalId() + ", " +
//                            button.getButtonId() + ")", aie);
            }
            //			lastButton = button;
        }

        void setButtonValue(String name, boolean isPressed) {
            ButtonView view = buttons.get(name);
            if (view != null) {
                if (isPressed) {
                    view.down();
                } else {
                    view.up();
                }
            }
        }

        void setXAxis(float f) {
            xAxis = f;
            resetPositions();
        }

        void setYAxis(float f) {
            yAxis = f;
            resetPositions();
        }

        void setZAxis(float f) {
            zAxis = f;
            resetPositions();
        }

        void setZRotation(float f) {
            zRotation = f;
            resetPositions();
        }

        private void resetPositions() {

            float xBase = 155;
            float yBase = 212;

            Vector2f dir = new Vector2f(xAxis, yAxis);
            float length = Math.min(1, dir.length());
            dir.normalizeLocal();

            float angle = dir.getAngle();
            float x = FastMath.cos(angle) * length * 10;
            float y = FastMath.sin(angle) * length * 10;
            leftStick.setLocalTranslation(xBase + x, yBase + y, 0);

            xBase = 291;
            dir = new Vector2f(zAxis, zRotation);
            length = Math.min(1, dir.length());
            dir.normalizeLocal();

            angle = dir.getAngle();
            x = FastMath.cos(angle) * length * 10;
            y = FastMath.sin(angle) * length * 10;
            rightStick.setLocalTranslation(xBase + x, yBase + y, 0);
        }
    }

    /**
     * Applied to the buttons to highlight which ones are being pressed.
     */
    private class ButtonView extends Node {
        private final ColorRGBA hilite =
                new ColorRGBA(0.0f, 0.75f, 0.75f, 0.5f);
        private int state = 0;
        private Material material;

        ButtonView(String name, float x, float y, float width, float height) {
            super("Button:" + name);
            setLocalTranslation(x, y, -0.5f);

            this.material = new Material(assetManager,
                    "Common/MatDefs/Misc/Unshaded.j3md");
            this.material.setColor("Color", hilite);
            this.material.getAdditionalRenderState()
                    .setBlendMode(BlendMode.Alpha);

            Geometry g = new Geometry("highlight", new Quad(width, height));
            g.setMaterial(this.material);
            g.setUserData("view", this);
            attachChild(g);

            resetState();
        }

        /**
         * Updates the visual of whether the button is pressed or not.
         */
        private void resetState() {
            if (state <= 0) {
                setCullHint(CullHint.Always);
            } else {
                setCullHint(CullHint.Dynamic);
            }

            //			System.out.println(getName() + " state:" + state);
        }

        /**
         * Checks to see if the visual displays if the button is pressed.
         *
         * @return True if the visual displays that the button is pressed,
         * false otherwise.
         */
        public boolean isDown() {
            return this.state > 0;
        }

        /**
         * Updates the button to display that it is pressed.
         *
         * @see #up()
         */
        public void down() {
            this.state++;
            this.resetState();
        }

        /**
         * Updates the button to display that it is not pressed.
         *
         * @see #down()
         */
        public void up() {
            this.state--;
            this.resetState();
        }
    }

    private void pickGamePad(Vector2f mouseLoc) {
//        if (lastButton != null)
//        {
//            CollisionResults cresults = pick(cam, mouseLoc, gamepad);
//            for (CollisionResult cr : cresults)
//            {
//                Node n = cr.getGeometry().getParent();
//                if (n != null && (n instanceof ButtonView))
//                {
//                    String b = n.getName().substring("Button:".length());
//                    String name = lastButton.getJoystick().getName()
//                            .replaceAll(" ", "\\\\ ");
//                    String id =
//                            lastButton.getLogicalId().replaceAll(" ", "\\\\ ");
//                    System.out.println(name + "." + id + "=" + b);
//                    return;
//                }
//            }
//        }
    }

    private static CollisionResults pick(Camera cam, Vector2f mouseLoc,
                                         Node node) {
        CollisionResults results = new CollisionResults();
        Ray ray = new Ray();
        Vector3f pos = new Vector3f(mouseLoc.x, mouseLoc.y, -1);
        Vector3f dir = new Vector3f(mouseLoc.x, mouseLoc.y, 1);
        dir.subtractLocal(pos).normalizeLocal();
        ray.setOrigin(pos);
        ray.setDirection(dir);
        node.collideWith(ray, results);
        return results;
    }
}
