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

package org.neo4j.ogm.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.neo4j.ogm.exception.core.MappingException;
import org.neo4j.ogm.metadata.ClassInfo;
import org.neo4j.ogm.metadata.FieldInfo;
import org.neo4j.ogm.metadata.MetaData;
import org.neo4j.ogm.metadata.reflect.EntityAccessManager;
import org.neo4j.ogm.metadata.reflect.EntityFactory;
import org.neo4j.ogm.model.RowModel;
import org.neo4j.ogm.session.EntityInstantiator;
import org.neo4j.ogm.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple graph-to-entity mapper suitable for ad-hoc, one-off mappings.  This doesn't interact with a
 * mapping context or mandate graph IDs on the target types and is not designed for use in the OGM session.
 *
 * @author Adam George
 * @author Luanne Misquitta
 */
public class SingleUseEntityMapper {

    private static final Logger logger = LoggerFactory.getLogger(SingleUseEntityMapper.class);

    private final EntityFactory entityFactory;
    private final MetaData metadata;
    /**
     * This is a supplier for a predicate that can determine whether a named property is a constructor argument or not.
     * As this mapper here was meant to be a "single use" instance, the predicate itself could be carried around as well,
     * but in the end: Better safe, than sorry.
     */
    private final Function<Class<?>, Optional<Predicate<String>>> constructorArgumentPredicateSupplier;

    /**
     * Compatibility constructor for SDN 5.0
     *
     * @param mappingMetaData The {@link MetaData} to use for performing mappings
     * @param entityFactory   The entity factory to use.
     */
    public SingleUseEntityMapper(MetaData mappingMetaData, EntityFactory entityFactory) {
        this.metadata = mappingMetaData;
        this.entityFactory = new EntityFactory(mappingMetaData);
        this.constructorArgumentPredicateSupplier = clazz -> Optional.empty();
    }

    /**
     * Constructs a new {@link SingleUseEntityMapper} based on the given mapping {@link MetaData}.
     *
     * @param mappingMetaData The {@link MetaData} to use for performing mappings
     * @param entityInstantiator   The entity factory to use.
     */
    public SingleUseEntityMapper(MetaData mappingMetaData, EntityInstantiator entityInstantiator) {
        this.metadata = mappingMetaData;
        this.entityFactory = new EntityFactory(mappingMetaData, entityInstantiator);
        this.constructorArgumentPredicateSupplier = entityInstantiator.getConstructorArgumentPredicateSupplier();
    }

    /**
     * Maps a row-based result onto a new instance of the specified type.
     *
     * @param <T>         The class of object to return
     * @param type        The {@link Class} denoting the type of object to create
     * @param columnNames The names of the columns in each row of the result
     * @param rowModel    The {@link org.neo4j.ogm.model.RowModel} containing the data to map
     * @return A new instance of <tt>T</tt> populated with the data in the specified row model
     */
    public <T> T map(Class<T> type, String[] columnNames, RowModel rowModel) {
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < rowModel.getValues().length; i++) {
            properties.put(columnNames[i], rowModel.getValues()[i]);
        }

        T entity = this.entityFactory.newObject(type, properties);
        setPropertiesOnEntity(entity, properties);
        return entity;
    }

    public <T> T map(Class<T> type, Map<String, Object> row) {
        T entity = this.entityFactory.newObject(type, row);
        setPropertiesOnEntity(entity, row);
        return entity;
    }

    private void setPropertiesOnEntity(Object entity, Map<String, Object> propertyMap) {
        Class<?> entityClass = entity.getClass();
        ClassInfo entityClassInfo = resolveClassInfoFor(entityClass);
        Predicate<String> isConstructorArgument = this.constructorArgumentPredicateSupplier
            .apply(entityClass).orElseGet(() -> name -> false);

        propertyMap.entrySet().stream()
            .filter(entry -> !isConstructorArgument.test(entry.getKey()))
            .forEach(entry -> writeProperty(entityClassInfo, entity, entry));
    }

    private ClassInfo resolveClassInfoFor(Class<?> type) {
        ClassInfo classInfo = this.metadata.classInfo(type.getSimpleName());
        if (classInfo != null) {
            return classInfo;
        }
        throw new MappingException("Cannot map query result to a class not known by Neo4j-OGM.");
    }

    private void writeProperty(ClassInfo classInfo, Object instance, Map.Entry<String, Object> property) {

        String propertyName = property.getKey();
        FieldInfo targetFieldInfo = classInfo.getFieldInfo(propertyName);

        if (targetFieldInfo == null) {
            targetFieldInfo = classInfo.relationshipFieldByName(propertyName);
        }

        // When mapping query results to objects that are not domain entities, there's no concept of a GraphID
        if (targetFieldInfo == null && "id".equals(propertyName)) {
            targetFieldInfo = classInfo.identityField();
        }

        if (targetFieldInfo == null) {
            logger.debug("Unable to find property: {} on class: {} for writing", propertyName, classInfo.name());
        } else {
            Object value = property.getValue();
            if (value != null && value.getClass().isArray()) {
                value = Arrays.asList((Object[]) value);
            }
            if (targetFieldInfo.type().isArray() || Iterable.class.isAssignableFrom(targetFieldInfo.type())) {
                Class elementType = underlyingElementType(classInfo, propertyName);
                value = targetFieldInfo.type().isArray()
                    ? EntityAccessManager.merge(targetFieldInfo.type(), value, new Object[] {}, elementType)
                    : EntityAccessManager.merge(targetFieldInfo.type(), value, Collections.EMPTY_LIST, elementType);
            }
            targetFieldInfo.write(instance, value);
        }
    }

    private Class underlyingElementType(ClassInfo classInfo, String propertyName) {
        FieldInfo fieldInfo = classInfo.propertyField(propertyName);
        if (fieldInfo != null) {
            return ClassUtils.getType(fieldInfo.getTypeDescriptor());
        }
        return classInfo.getUnderlyingClass();
    }
}
