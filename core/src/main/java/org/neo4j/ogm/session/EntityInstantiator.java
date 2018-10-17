/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 *  conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package org.neo4j.ogm.session;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Interface to be implemented to override entity instances creation.
 * This is mainly designed for SDN, Spring data commons having some infrastructure code to do fancy
 * object instantiation using persistence constructors and ASM low level bytecode generation.
 *
 * @author Nicolas Mervaillie
 * @author Michael J. Simons
 * @since 3.1
 */
public interface EntityInstantiator {

    /**
     * Creates an instance of a given class.
     *
     * @param clazz          The class to materialize.
     * @param propertyValues Properties of the object (needed for constructors with args)
     * @param <T>            Type to create
     * @return The created instance.
     */
    <T> T createInstance(Class<T> clazz, Map<String, Object> propertyValues);

    /**
     * This method shall return a function that returns a predicate for a given class
     * that determines if a property with a given name is a constructor argument for that class or not.
     * If such a predicate cannot be provided, the implementing class may return an empty optional.
     *
     * @since 3.1.5
     * @return A function that returns a false Predicate for all classes and property names
     */
    default Function<Class<?>, Optional<Predicate<String>>> getConstructorArgumentPredicateSupplier() {
        return clazz -> Optional.empty();
    }
}
