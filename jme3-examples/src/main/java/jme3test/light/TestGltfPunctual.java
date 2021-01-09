package jme3test.light;

import com.jme3.app.SimpleApplication;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.shadow.PointLightShadowRenderer;
import com.jme3.shadow.SpotLightShadowRenderer;

public class TestGltfPunctual extends SimpleApplication implements ActionListener {
    private Node scene;
    public static void main(String[] args) {
        TestGltfPunctual testUnlit = new TestGltfPunctual();
        testUnlit.start();
    }

    @Override
    public void simpleInitApp() {
        final int SHADOWMAP_SIZE = 2048;
        Node scene;
        DirectionalLightShadowRenderer dlsr;
        SpotLightShadowRenderer slsr;
        PointLightShadowRenderer plsr;

        this.cam.setLocation(new Vector3f(0, 10, 20));

        scene = (Node) this.getAssetManager().loadModel("jme3test/scenes/punctual.gltf");
        scene.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        this.rootNode.attachChild(scene);
        for (Light light : scene.getLocalLightList()) {
            System.out.println("Adding processor for " + light);
            if (light instanceof DirectionalLight) {
                dlsr = new DirectionalLightShadowRenderer(assetManager, SHADOWMAP_SIZE, 3);
                dlsr.setLight((DirectionalLight) light);
                dlsr.setLambda(0.55f);
                dlsr.setShadowIntensity(0.8f);
                dlsr.setEdgeFilteringMode(EdgeFilteringMode.Nearest);
                viewPort.addProcessor(dlsr);
            }
            else if (light instanceof SpotLight) {
                slsr = new SpotLightShadowRenderer(assetManager, 512);
                slsr.setLight((SpotLight) light);
                slsr.setShadowIntensity(0.8f);
                slsr.setShadowZExtend(100);
                slsr.setShadowZFadeLength(5);
                slsr.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
                viewPort.addProcessor(slsr);
            }
            else if (light instanceof PointLight) {
                plsr = new PointLightShadowRenderer(assetManager, SHADOWMAP_SIZE);
                plsr.setLight((PointLight) light);
                plsr.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
                plsr.setShadowZExtend(100);
                plsr.setShadowZFadeLength(5);
                plsr.setShadowIntensity(0.8f);
                // plsr.setFlushQueues(false);
                //plsr.displayFrustum();
                viewPort.addProcessor(plsr);
            }
        }
//        this.viewPort.detachScene(this.rootNode);
//        this.viewPort.attachScene(scene);

        this.debugShadowMode(scene);

        ColorRGBA skyColor = new ColorRGBA(0.5f, 0.6f, 0.7f, 0.0f);

        flyCam.setMoveSpeed(20);
        viewPort.setBackgroundColor(skyColor.mult(0.9f));
    }

    private void debugShadowMode(Spatial scene) {
        debugShadowMode(scene, 0);
    }

    private void debugShadowMode(Spatial scene, int level) {
        for (int i = 0; i < level; i++) {
            System.out.print('\t');
        }
        System.out.print(scene.getName());
        if (scene instanceof Geometry)
        System.out.print(" - " + ((Geometry) scene).getMaterial());
        System.out.println();
        for (Light child : scene.getLocalLightList()) {
            for (int i = 0; i < level + 1; i++) {
                System.out.print('\t');
            }
            System.out.println(child.getName() + " - " + child);
        }
        if (scene instanceof Node) {
            for (Spatial child : ((Node) scene).getChildren()) {
                debugShadowMode(child, level + 1);
            }
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
    }
}