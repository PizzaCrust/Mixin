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
package org.spongepowered.tools.obfuscation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.tools.MirrorUtils;
import org.spongepowered.tools.obfuscation.interfaces.IMixinAnnotationProcessor;
import org.spongepowered.tools.obfuscation.interfaces.IMixinValidator;
import org.spongepowered.tools.obfuscation.interfaces.IMixinValidator.ValidationPass;
import org.spongepowered.tools.obfuscation.interfaces.IObfuscationManager;
import org.spongepowered.tools.obfuscation.interfaces.ITypeHandleProvider;
import org.spongepowered.tools.obfuscation.struct.Message;

/**
 * Information about a mixin stored during processing
 */
class AnnotatedMixin {
    
    /**
     * Mixin annotation
     */
    private final AnnotationMirror annotation;
    
    /**
     * Messager 
     */
    private final Messager messager;
    
    /**
     * Type handle provider
     */
    private final ITypeHandleProvider typeProvider;
    
    /**
     * Manager
     */
    private final IObfuscationManager obf;
    
    /**
     * Mixin class
     */
    private final TypeElement mixin;
    
    /**
     * Mixin class
     */
    private final TypeHandle handle;

    /**
     * Specified targets
     */
    private final List<TypeHandle> targets = new ArrayList<TypeHandle>();
    
    /**
     * Target "reference" (bytecode name)
     */
    private final String targetRef;
    
    /**
     * Target type (for single-target mixins) 
     */
    private final TypeHandle targetType;
    
    /**
     * Mixin class "reference" (bytecode name)
     */
    private final String classRef;
    
    /**
     * True if we will actually process remappings for this mixin
     */
    private final boolean remap;
    
    /**
     * Stored (ordered) field mappings
     */
    private final Map<ObfuscationType, Set<String>> fieldMappings = new HashMap<ObfuscationType, Set<String>>();
    
    /**
     * Stored (ordered) method mappings
     */
    private final Map<ObfuscationType, Set<String>> methodMappings = new HashMap<ObfuscationType, Set<String>>();
    
    /**
     * Overwrite handler
     */
    private final AnnotatedMixinOverwriteHandler overwrites;
    
    /**
     * Shadow handler
     */
    private final AnnotatedMixinShadowHandler shadows;
    
    /**
     * Injector handler
     */
    private final AnnotatedMixinInjectorHandler injectors;

    public AnnotatedMixin(IMixinAnnotationProcessor ap, TypeElement type) {
        this.annotation = MirrorUtils.getAnnotation(type, Mixin.class);
        this.typeProvider = ap.getTypeProvider();
        this.obf = ap.getObfuscationManager();
        this.messager = ap;
        this.mixin = type;
        this.handle = new TypeHandle(type);
        this.classRef = type.getQualifiedName().toString().replace('.', '/');
        
        TypeHandle primaryTarget = this.initTargets();
        if (primaryTarget != null) {
            this.targetRef = primaryTarget.getName();
            this.targetType = primaryTarget; 
        } else {
            this.targetRef = null;
            this.targetType = null;
        }

        this.remap = AnnotatedMixins.getRemapValue(this.annotation) && this.targets.size() > 0;
        
        for (ObfuscationType obfType : ObfuscationType.values()) {
            this.fieldMappings.put(obfType, new LinkedHashSet<String>());
            this.methodMappings.put(obfType, new LinkedHashSet<String>());
        }
        
        this.overwrites = new AnnotatedMixinOverwriteHandler(ap, this);
        this.shadows = new AnnotatedMixinShadowHandler(ap, this);
        this.injectors = new AnnotatedMixinInjectorHandler(ap, this);
    }

    AnnotatedMixin runValidators(ValidationPass pass, Collection<IMixinValidator> validators) {
        for (IMixinValidator validator : validators) {
            if (!validator.validate(pass, this.mixin, this.annotation, this.targets)) {
                break;
            }
        }
        
        return this;
    }

    private TypeHandle initTargets() {
        TypeHandle primaryTarget = null;
        
        // Public targets, referenced by class
        try {
            List<AnnotationValue> publicTargets = MirrorUtils.<List<AnnotationValue>>getAnnotationValue(this.annotation, "value",
                    Collections.<AnnotationValue>emptyList());
            for (TypeMirror target : MirrorUtils.<TypeMirror>unfold(publicTargets)) {
                TypeHandle type = new TypeHandle((DeclaredType)target);
                if (this.targets.contains(type)) {
                    continue;
                }
                this.addTarget(type);
                if (primaryTarget == null) {
                    primaryTarget = type;
                }
            }
        } catch (Exception ex) {
            this.printMessage(Kind.WARNING, "Error processing public targets: " + ex.getClass().getName() + ": " + ex.getMessage(), this);
        }
        
        // Private targets, referenced by name
        try {
            List<AnnotationValue> privateTargets = MirrorUtils.<List<AnnotationValue>>getAnnotationValue(this.annotation, "targets",
                    Collections.<AnnotationValue>emptyList());
            for (String privateTarget : MirrorUtils.<String>unfold(privateTargets)) {
                TypeHandle type = this.typeProvider.getTypeHandle(privateTarget);
                if (this.targets.contains(type)) {
                    continue;
                }
                if (type == null) {
                    this.printMessage(Kind.ERROR, "Mixin target " + privateTarget + " could not be found", this);
                    return null;
                } else if (type.isPublic()) {
                    this.printMessage(Kind.ERROR, "Mixin target " + privateTarget + " is public and must be specified in value", this);
                    return null;
                }
                this.addSoftTarget(type, privateTarget);
                if (primaryTarget == null) {
                    primaryTarget = type;
                }
            }
        } catch (Exception ex) {
            this.printMessage(Kind.WARNING, "Error processing private targets: " + ex.getClass().getName() + ": " + ex.getMessage(), this);
        }
        
        if (primaryTarget == null) {
            this.printMessage(Kind.ERROR, "Mixin has no targets", this);
        }
        
        return primaryTarget;
    }

    /**
     * Print a message to the AP messager
     */
    private void printMessage(Kind kind, CharSequence msg, AnnotatedMixin mixin) {
        this.messager.printMessage(kind, msg, this.mixin, this.annotation);
    }

    private void addSoftTarget(TypeHandle type, String reference) {
        ObfuscationData<String> obfClassData = this.obf.getObfClass(type);
        if (!obfClassData.isEmpty()) {
            this.obf.addClassMapping(this.classRef, reference, obfClassData);
        }
        
        this.addTarget(type);
    }
    
    private void addTarget(TypeHandle type) {
        this.targets.add(type);
    }

    @Override
    public String toString() {
        return this.mixin.getSimpleName().toString();
    }
    
    public AnnotationMirror getAnnotation() {
        return this.annotation;
    }
    
    /**
     * Get the mixin class
     */
    public TypeElement getMixin() {
        return this.mixin;
    }
    
    /**
     * Get the type handle for the mixin class
     */
    public TypeHandle getHandle() {
        return this.handle;
    }
    
    /**
     * Get the mixin class reference
     */
    public String getClassRef() {
        return this.classRef;
    }
    
    /**
     * Get whether this is an interface mixin
     */
    public boolean isInterface() {
        return this.mixin.getKind() == ElementKind.INTERFACE;
    }
    
    /**
     * Get the <em>primary</em> target class reference
     */
    public String getPrimaryTargetRef() {
        return this.targetRef;
    }
    
    /**
     * Get the <em>primary</em> target
     */
    public TypeHandle getPrimaryTarget() {
        return this.targetType;
    }
    
    /**
     * Get the mixin's targets
     */
    public List<TypeHandle> getTargets() {
        return this.targets;
    }
    
    /**
     * Get whether to remap annotations in this mixin
     */
    public boolean remap() {
        return this.remap;
    }
    
    /**
     * Get stored field mappings
     */
    public Set<String> getFieldMappings(ObfuscationType type) {
        return this.fieldMappings.get(type);
    }
    
    /**
     * Get stored method mappings
     */
    public Set<String> getMethodMappings(ObfuscationType type) {
        return this.methodMappings.get(type);
    }
    
    /**
     * Clear all stored mappings
     */
    public void clear() {
        this.fieldMappings.clear();
        this.methodMappings.clear();
    }

    public void registerOverwrite(ExecutableElement method, AnnotationMirror overwrite) {
        this.overwrites.registerOverwrite(method, overwrite);
    }

    public void registerShadow(VariableElement field, AnnotationMirror shadow, boolean shouldRemap) {
        this.shadows.registerShadow(field, shadow, shouldRemap);
    }

    public void registerShadow(ExecutableElement method, AnnotationMirror shadow, boolean shouldRemap) {
        this.shadows.registerShadow(method, shadow, shouldRemap);
    }

    public Message registerInjector(ExecutableElement method, AnnotationMirror inject, boolean remap) {
        return this.injectors.registerInjector(method, inject, remap);
    }

    public int registerInjectionPoint(ExecutableElement element, AnnotationMirror inject, AnnotationMirror at) {
        return this.injectors.registerInjectionPoint(element, inject, at);
    }

    void addFieldMapping(ObfuscationType type, String mapping) {
        this.fieldMappings.get(type).add(mapping);
    }

    void addMethodMapping(ObfuscationType type, String mapping) {
        this.methodMappings.get(type).add(mapping);
    }
    
}
