/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.launch;

import java.net.URI;

import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * Default launch agent, handles the <tt>MixinConfigs</tt> manifest key
 */
public class MixinLaunchAgentDefault extends MixinLaunchAgentAbstract {

    protected static final String MFATT_MIXINCONFIGS = "MixinConfigs";
    protected static final String MFATT_TOKENPROVIDERS = "MixinTokenProviders";
    protected static final String MFATT_MAINCLASS = "Main-Class";
    protected static final String MFATT_COMPATIBILITY = "MixinCompatibilityLevel";

    public MixinLaunchAgentDefault(URI uri) {
        super(uri);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void prepare() {
        String compatibilityLevel = this.attributes.get(MixinLaunchAgentDefault.MFATT_COMPATIBILITY);
        if (compatibilityLevel != null) {
            MixinLaunchAgentAbstract.logger.warn("{} Setting mixin compatibility level via manifest is deprecated, "
                    + "use 'compatibilityLevel' key in config instead", this.container);
            MixinTweaker.setCompatibilityLevel(compatibilityLevel);
        }
        
        String mixinConfigs = this.attributes.get(MixinLaunchAgentDefault.MFATT_MIXINCONFIGS);
        if (mixinConfigs != null) {
            for (String config : mixinConfigs.split(",")) {
                MixinTweaker.addConfig(config.trim());
            }
        }
        
        String tokenProviders = this.attributes.get(MixinLaunchAgentDefault.MFATT_TOKENPROVIDERS);
        if (tokenProviders != null) {
            for (String provider : tokenProviders.split(",")) {
                MixinTweaker.addTokenProvider(provider.trim());
            }
        }
    }
    
    @Override
    public void initPrimaryContainer() {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
    }
    
    @Override
    public String getLaunchTarget() {
        return this.attributes.get(MixinLaunchAgentDefault.MFATT_MAINCLASS);
    }

}
