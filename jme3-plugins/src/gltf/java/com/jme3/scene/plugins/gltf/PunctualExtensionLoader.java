/*
 * Copyright (c) 2009-2020 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.scene.plugins.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jme3.asset.AssetKey;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Markil 3
 */
public class PunctualExtensionLoader implements ExtensionLoader {

    private final HashMap<Spatial, Integer> lightObs = new HashMap<>();

    @Override
    public Object handleExtension(GltfLoader loader, String parentName, JsonElement parent, JsonElement extension, Object input) throws IOException {
        JsonArray lightsOb;
        JsonObject lightOb;
        Light light = null;
        Light[] lights;
        int i;
        ColorRGBA color;

        if (extension.getAsJsonObject().has("light")) {
            /*
             * Save this for when we load the lights list at the end.
             */
            lightObs.put((Spatial) input, extension.getAsJsonObject().get("light").getAsInt());
        } else if (extension.getAsJsonObject().has("lights")) {
            lightsOb = extension.getAsJsonObject().get("lights").getAsJsonArray();
            lights = new Light[lightsOb.size()];
            i = 0;

            /*
             * Find every light type in the scene.
             */
            for (JsonElement el : lightsOb.getAsJsonArray()) {
                lightOb = el.getAsJsonObject();
                color = GltfUtils.getAsColor(lightOb, "color", ColorRGBA.White).multLocal(Optional.ofNullable(lightOb.get("intensity")).map(JsonElement::getAsFloat).orElse(1F));
                switch (lightOb.get("type").getAsString()) {
                    case "directional":
                        light = new DirectionalLight(new Vector3f(), color);
                        break;
                    case "spot":
                        /*
                         * The high values that Blender exports can be pretty powerful in JME. Scale them down a bit.
                         */
                        light = new SpotLight(new Vector3f(), new Vector3f(), color.mult(1F / 1000F));
                        if (lightOb.has("range")) {
                            ((SpotLight) light).setSpotRange(lightOb.get("range").getAsFloat());
                        }
                        ((SpotLight) light).setSpotInnerAngle(lightOb.get("spot").getAsJsonObject().get("innerConeAngle").getAsFloat());
                        ((SpotLight) light).setSpotOuterAngle(lightOb.get("spot").getAsJsonObject().get("outerConeAngle").getAsFloat());
                        break;
                    case "point":
                        /*
                         * The high values that Blender exports can be pretty powerful in JME. Scale them down a bit.
                         */
                        light = new PointLight(new Vector3f(), color.mult(1F / 2000F));
                        if (lightOb.has("range")) {
                            ((PointLight) light).setRadius(lightOb.get("range").getAsFloat());
                        }
                        break;
                }
                if (lightOb.has("name")) {
                    light.setName(lightOb.get("name").getAsString());
                }
                lights[i++] = light;
            }

            /*
             * Add the light objects to the scene
             */
            for (Map.Entry<Spatial, Integer> lightEntry : this.lightObs.entrySet()) {
                light = lights[lightEntry.getValue()].clone();
                if (light instanceof DirectionalLight) {
                    ((DirectionalLight) light).setDirection(lightEntry.getKey().getParent().getLocalRotation().getRotationColumn(1).mult(-1));
                } else if (light instanceof SpotLight) {
                    ((SpotLight) light).setDirection(lightEntry.getKey().getParent().getLocalRotation().getRotationColumn(1).mult(-1));
                    ((SpotLight) light).setPosition(lightEntry.getKey().getParent().getLocalTranslation());
                } else if (light instanceof PointLight) {
                    ((PointLight) light).setPosition(lightEntry.getKey().getParent().getLocalTranslation());
                }
                /*
                 * The node witht he light data is simply an information node for lighting. Might as well delete it.
                 */
                lightEntry.getKey().getParent().getParent().addLight(light);
                lightEntry.getKey().getParent().removeFromParent();
            }

            this.lightObs.clear();
        }
        return input;
    }
}
