/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.env;

/**
 * Interface for resolving properties against any underlying source.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see Environment
 * @see PropertySourcesPropertyResolver
 * 属解析接口，返回属性是否可解析，默认string属性解析，指定返回类型属性解析，占位替换解析功能接口
 */
public interface PropertyResolver {

	/**
	 * 判断给定的资源键是否可用于解析
	 * Return whether the given property key is available for resolution,
	 * i.e. if the value for the given key is not {@code null}.
	 */
	boolean containsProperty(String key);

	/**
	 *
	 * 返回指定的资源键的属性值，结果为String 类型，无法解析，返回null
	 * Return the property value associated with the given key,
	 * or {@code null} if the key cannot be resolved.
	 * @param key the property name to resolve
	 * @see #getProperty(String, String)
	 * @see #getProperty(String, Class)
	 * @see #getRequiredProperty(String)
	 */
	String getProperty(String key);

	/**
	 * 返回给定属性键关联的属性值，如果不存在，返回默认值
	 * Return the property value associated with the given key, or
	 * {@code defaultValue} if the key cannot be resolved.
	 * @param key the property name to resolve
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String)
	 * @see #getProperty(String, Class)
	 */
	String getProperty(String key, String defaultValue);

	/**
	 *  返回指定的资源键的属性值，结果为指定 类型，无法解析，返回null
	 * Return the property value associated with the given key,
	 * or {@code null} if the key cannot be resolved.
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * @see #getRequiredProperty(String, Class)
	 */
	<T> T getProperty(String key, Class<T> targetType);

	/**
	 * 返回指定的资源键的属性值，结果为指定类型，无法解析，返回默认值
	 * Return the property value associated with the given key,
	 * or {@code defaultValue} if the key cannot be resolved.
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String, Class)
	 */
	<T> T getProperty(String key, Class<T> targetType, T defaultValue);

	/**
	 * 返会资源键值对应的class 类
	 * Convert the property value associated with the given key to a {@code Class}
	 * of type {@code T} or {@code null} if the key cannot be resolved.
	 * @throws org.springframework.core.convert.ConversionException if class specified
	 * by property value cannot be found or loaded or if targetType is not assignable
	 * from class specified by property value
	 * @see #getProperty(String, Class)
	 * @deprecated as of 4.3, in favor of {@link #getProperty} with manual conversion
	 * to {@code Class} via the application's {@code ClassLoader}
	 */
	@Deprecated
	<T> Class<T> getPropertyAsClass(String key, Class<T> targetType);

	/**
	 * 返回指定的资源键的属性值，结果为String 类型，无法解析，抛出异常
	 * Return the property value associated with the given key (never {@code null}).
	 * @throws IllegalStateException if the key cannot be resolved
	 * @see #getRequiredProperty(String, Class)
	 */
	String getRequiredProperty(String key) throws IllegalStateException;

	/**
	 * 返回指定的资源键的属性值，结果为指定 类型，无法解析，抛出异常
	 * Return the property value associated with the given key, converted to the given
	 * targetType (never {@code null}).
	 * @throws IllegalStateException if the given key cannot be resolved
	 */
	<T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException;

	/**
	 * 解析文本中的 ${...}占位符，用getProperty 方法解析的属性值替换
	 *     *没有默认值的的占位符将会被忽略并保持不变
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding
	 * property values as resolved by {@link #getProperty}. Unresolvable placeholders with
	 * no default value are ignored and passed through unchanged.
	 * @param text the String to resolve
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * @see #resolveRequiredPlaceholders
	 * @see org.springframework.util.SystemPropertyUtils#resolvePlaceholders(String)
	 */
	String resolvePlaceholders(String text);

	/**解析文本中的 ${...}占位符，用getProperty 方法解析的属性值替换
	 *没有默认值的不可解析的占位符抛出异常
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding
	 * property values as resolved by {@link #getProperty}. Unresolvable placeholders with
	 * no default value will cause an IllegalArgumentException to be thrown.
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * or if any placeholders are unresolvable
	 * @see org.springframework.util.SystemPropertyUtils#resolvePlaceholders(String, boolean)
	 */
	String resolveRequiredPlaceholders(String text) throws IllegalArgumentException;

}
