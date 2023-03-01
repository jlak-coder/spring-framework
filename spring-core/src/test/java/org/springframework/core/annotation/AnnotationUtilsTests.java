/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.subpackage.NonPublicAnnotatedClass;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.core.annotation.AnnotationUtils.*;
import static org.springframework.core.annotation.AnnotationUtils.getAttributeAliasNames;

/**
 * Unit tests for {@link AnnotationUtils}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Chris Beams
 * @author Phillip Webb
 * @author Oleg Zhurakousky
 */
public class AnnotationUtilsTests {

	@Rule
	public final ExpectedException exception = ExpectedException.none();


	@Before
	public void clearCacheBeforeTests() {
		AnnotationUtils.clearCache();
	}


	@Test
	public void findMethodAnnotationOnLeaf() throws Exception {
		//在该实现类的的注解，最底层叶子节点上的注解
		Method m = Leaf.class.getMethod("annotatedOnLeaf");
		assertNotNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	// @since 4.2
	@Test
	public void findMethodAnnotationWithAnnotationOnMethodInInterface() throws Exception {
		//在接口上的注解
		Method m = Leaf.class.getMethod("fromInterfaceImplementedByRoot");
		// @Order is not @Inherited
		//在Method方法的注解上无法找到，不继承 接口的注解
		assertNull(m.getAnnotation(Order.class));
		// getAnnotation() does not search on interfaces
		//getAnnotation 方法不在接口层面搜索
		assertNull(getAnnotation(m, Order.class));
		// findAnnotation() does search on interfaces
		//findAnnotation 在接口层面搜索
		assertNotNull(findAnnotation(m, Order.class));
	}

	// @since 4.2
	@Test
	public void findMethodAnnotationWithMetaAnnotationOnLeaf() throws Exception {
		//在一个方法上查到不存在的注解
		Method m = Leaf.class.getMethod("metaAnnotatedOnLeaf");
		assertNull(m.getAnnotation(Order.class));
		//从提供的 Method中获取单个 Annotation ,向上遍历注解的上的自定义注解
		assertNotNull(getAnnotation(m, Order.class));
		//findAnnotation 在接口层面搜索
		assertNotNull(findAnnotation(m, Order.class));
	}

	// @since 4.2
	@Test
	public void findMethodAnnotationWithMetaMetaAnnotationOnLeaf() throws Exception {
		Method m = Leaf.class.getMethod("metaMetaAnnotatedOnLeaf");
		assertNull(m.getAnnotation(Component.class));
		//从提供的 Method中获取单个 Annotation ，向上遍历注解的上的自定义注解。
		assertNull(getAnnotation(m, Component.class));
		//findAnnotation 支持
		assertNotNull(findAnnotation(m, Component.class));
	}

	@Test
	public void findMethodAnnotationOnRoot() throws Exception {
		//父类上子类没有重写的方法，三个方法都可以寻找到
		Method m = Leaf.class.getMethod("annotatedOnRoot");
		assertNotNull(m.getAnnotation(Order.class));
		assertNotNull(getAnnotation(m, Order.class));
		assertNotNull(findAnnotation(m, Order.class));
	}

	// @since 4.2
	@Test
	public void findMethodAnnotationWithMetaAnnotationOnRoot() throws Exception {
		//父类自定义注解
		Method m = Leaf.class.getMethod("metaAnnotatedOnRoot");
		//@Order getAnnotation 不支持深入遍历元注解
		assertNull(m.getAnnotation(Order.class));
		//getAnnotation支持向上遍历
		assertNotNull(getAnnotation(m, Order.class));
		//支持
		assertNotNull(findAnnotation(m, Order.class));
	}

	@Test
	public void findMethodAnnotationOnRootButOverridden() throws Exception {
		//子类重写，且子类无注解
		Method m = Leaf.class.getMethod("overrideWithoutNewAnnotation");
		//
		assertNull(m.getAnnotation(Order.class));
		//只有类级别的注解会被继承得到，对于其他对象而言，getAnnotation() 方法与 getDeclaredAnnotation() 方法作用相同
		System.out.println(Arrays.toString(m.getAnnotations()));
		System.out.println(Arrays.toString(m.getDeclaredAnnotations()));
		//getAnnotation 不在父类寻找
		assertNull(getAnnotation(m, Order.class));
		//父类和接口 都寻找
		assertNotNull(findAnnotation(m, Order.class));
	}

	@Test
	public void findMethodAnnotationNotAnnotated() throws Exception {
		//无注解方法上查询，查询不到
		Method m = Leaf.class.getMethod("notAnnotated");
		assertNull(findAnnotation(m, Order.class));
	}

	/**
	 * 桥接方法上的 注解寻找
	 * @throws Exception
	 */
	@Test
	public void findMethodAnnotationOnBridgeMethod() throws Exception {
		//桥接方法
		Method bridgeMethod = SimpleFoo.class.getMethod("something", Object.class);
		assertTrue(bridgeMethod.isBridge());

		//在桥接方法寻找直接注解
		assertNull(bridgeMethod.getAnnotation(Order.class));
		//在桥接方法上寻找注解，并遍历元注解
		assertNull(getAnnotation(bridgeMethod, Order.class));
		//在桥接方法上寻找注解，并遍历元注解,搜索父类 和 接口
		assertNotNull(findAnnotation(bridgeMethod, Order.class));

		//是因为不同的编译器，可能会导致 桥编辑器生成的桥接方法对注解的处理结果不同，
		// 所以需要BridgeMethodResolver.findBridgedMethod 进行处理，寻找原始方法
		// Inconsistent behavior between javac and Eclipse compiler
		//assertNotNull(bridgeMethod.getAnnotation(Transactional.class));
		assertNotNull(getAnnotation(bridgeMethod, Transactional.class));
		assertNotNull(findAnnotation(bridgeMethod, Transactional.class));
	}

	@Test
	public void findMethodAnnotationOnBridgedMethod() throws Exception {
		Method bridgedMethod = SimpleFoo.class.getMethod("something", String.class);
		assertFalse(bridgedMethod.isBridge());

		assertNull(bridgedMethod.getAnnotation(Order.class));
		assertNull(getAnnotation(bridgedMethod, Order.class));
		// AnnotationUtils.findAnnotation(Method, Class<A>) will not find an annotation on
		// the bridge method for a bridged method.
		//bridgedMethod 无法在父类找到实现的接口方法
		assertNull(findAnnotation(bridgedMethod, Order.class));

		assertNotNull(bridgedMethod.getAnnotation(Transactional.class));
		assertNotNull(getAnnotation(bridgedMethod, Transactional.class));
		assertNotNull(findAnnotation(bridgedMethod, Transactional.class));
	}

	@Test
	public void findMethodAnnotationFromInterface() throws Exception {
		Method method = ImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		//findAnnotation 会搜索方法的 直接接口接口方法
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	@Test
	public void findMethodAnnotationFromInterfaceOnSuper() throws Exception {
		Method method = SubOfImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		//findAnnotation 会搜索方法的 父类接口方法
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	@Test
	public void findMethodAnnotationFromInterfaceWhenSuperDoesNotImplementMethod() throws Exception {
		Method method = SubOfAbstractImplementsInterfaceWithAnnotatedMethod.class.getMethod("foo");
		//父类没有实现方法 可以搜索到接口的注解
		Order order = findAnnotation(method, Order.class);
		assertNotNull(order);
	}

	// @since 4.1.2
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverAnnotationsOnInterfaces() {
		//在类上 搜索 注解
		Component component = findAnnotation(ClassWithLocalMetaAnnotationAndMetaAnnotatedInterface.class, Component.class);
		assertNotNull(component);
		assertEquals("meta2", component.value());
	}

	// @since 4.0.3
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverInheritedAnnotations() {
		//注解可以通过设置Inherited 继承
		Transactional transactional0 = SubSubClassWithInheritedAnnotation.class.getAnnotation(Transactional.class);
		assertNotNull(transactional0);
		//类上的  这里是搜索父类 和 Inherited的性质无关
		Transactional transactional = findAnnotation(SubSubClassWithInheritedAnnotation.class, Transactional.class);
		assertNotNull(transactional);
		assertTrue("readOnly flag for SubSubClassWithInheritedAnnotation", transactional.readOnly());
	}

	// @since 4.0.3
	@Test
	public void findClassAnnotationFavorsMoreLocallyDeclaredComposedAnnotationsOverInheritedComposedAnnotations() {
		Component component = findAnnotation(SubSubClassWithInheritedMetaAnnotation.class, Component.class);
		assertNotNull(component);
		//查询到 立即返回
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnMetaMetaAnnotatedClass() {
		//findAnnotation 支持递归查询元注解
		Component component = findAnnotation(MetaMetaAnnotatedClass.class, Component.class);
		assertNotNull("Should find meta-annotation on composed annotation on class", component);
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnMetaMetaMetaAnnotatedClass() {
		Component component = findAnnotation(MetaMetaMetaAnnotatedClass.class, Component.class);
		assertNotNull("Should find meta-annotation on meta-annotation on composed annotation on class", component);
		assertEquals("meta2", component.value());
	}

	@Test
	public void findClassAnnotationOnAnnotatedClassWithMissingTargetMetaAnnotation() {
		// TransactionalClass is NOT annotated or meta-annotated with @Component
		Component component = findAnnotation(TransactionalClass.class, Component.class);
		assertNull("Should not find @Component on TransactionalClass", component);
	}

	@Test
	public void findClassAnnotationOnMetaCycleAnnotatedClassWithMissingTargetMetaAnnotation() {
		//注解循环 依赖 测试
		Component component = findAnnotation(MetaCycleAnnotatedClass.class, Component.class);
		assertNull("Should not find @Component on MetaCycleAnnotatedClass", component);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnInheritedAnnotationInterface() {
		//在接口类上查找注解
		Transactional tx = findAnnotation(InheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on InheritedAnnotationInterface", tx);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnSubInheritedAnnotationInterface() {
		//在子接口上 寻找父类上的接口上的注解
		Transactional tx = findAnnotation(SubInheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on SubInheritedAnnotationInterface", tx);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnSubSubInheritedAnnotationInterface() {
		//在子接口上 查询父父类的注解
		Transactional tx = findAnnotation(SubSubInheritedAnnotationInterface.class, Transactional.class);
		assertNotNull("Should find @Transactional on SubSubInheritedAnnotationInterface", tx);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnNonInheritedAnnotationInterface() {
		//直接在接口上寻找
		Order order = findAnnotation(NonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on NonInheritedAnnotationInterface", order);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnSubNonInheritedAnnotationInterface() {
		//在父类接口查找
		Order order = findAnnotation(SubNonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on SubNonInheritedAnnotationInterface", order);
	}

	// @since 4.2
	@Test
	public void findClassAnnotationOnSubSubNonInheritedAnnotationInterface() {
		//多层 继承，在子类可以查询到父类注解
		Order order = findAnnotation(SubSubNonInheritedAnnotationInterface.class, Order.class);
		assertNotNull("Should find @Order on SubSubNonInheritedAnnotationInterface", order);
	}

	@Test
	public void findAnnotationDeclaringClassForAllScenarios() {
		// no class-level annotation
		assertNull(findAnnotationDeclaringClass(Transactional.class, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClass(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
				findAnnotationDeclaringClass(Transactional.class, InheritedAnnotationInterface.class));
		//接口 clazz.getSuperclass() 无发向上查看父类
		assertNull(findAnnotationDeclaringClass(Transactional.class, SubInheritedAnnotationInterface.class));
		//
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClass(Transactional.class, InheritedAnnotationClass.class));
		//注解在父类，并查找到
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClass(Transactional.class, SubInheritedAnnotationClass.class));

		//getDeclaredAnnotations @Inherited 不生效
		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClass() should still find it on classes.
		assertEquals(NonInheritedAnnotationInterface.class,
				findAnnotationDeclaringClass(Order.class, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClass(Order.class, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClass(Order.class, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClass(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void findAnnotationDeclaringClassForTypesWithSingleCandidateType() {
		// no class-level annotation
		List<Class<? extends Annotation>> transactionalCandidateList = Collections.singletonList(Transactional.class);
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
				findAnnotationDeclaringClassForTypes(transactionalCandidateList, InheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(transactionalCandidateList, SubInheritedAnnotationInterface.class));
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(transactionalCandidateList, InheritedAnnotationClass.class));
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(transactionalCandidateList, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClassForTypes() should still find it on classes.
		List<Class<? extends Annotation>> orderCandidateList = Collections.singletonList(Order.class);
		assertEquals(NonInheritedAnnotationInterface.class,
				findAnnotationDeclaringClassForTypes(orderCandidateList, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(orderCandidateList, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(orderCandidateList, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(orderCandidateList, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void findAnnotationDeclaringClassForTypesWithMultipleCandidateTypes() {
		List<Class<? extends Annotation>> candidates = asList(Transactional.class, Order.class);

		// no class-level annotation
		assertNull(findAnnotationDeclaringClassForTypes(candidates, NonAnnotatedInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertEquals(InheritedAnnotationInterface.class,
				findAnnotationDeclaringClassForTypes(candidates, InheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, SubInheritedAnnotationInterface.class));
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(candidates, InheritedAnnotationClass.class));
		assertEquals(InheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(candidates, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited,
		// but findAnnotationDeclaringClassForTypes() should still find it on classes.
		assertEquals(NonInheritedAnnotationInterface.class,
				findAnnotationDeclaringClassForTypes(candidates, NonInheritedAnnotationInterface.class));
		assertNull(findAnnotationDeclaringClassForTypes(candidates, SubNonInheritedAnnotationInterface.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(candidates, NonInheritedAnnotationClass.class));
		assertEquals(NonInheritedAnnotationClass.class,
				findAnnotationDeclaringClassForTypes(candidates, SubNonInheritedAnnotationClass.class));

		// class hierarchy mixed with @Transactional and @Order declarations
		assertEquals(TransactionalClass.class,
				findAnnotationDeclaringClassForTypes(candidates, TransactionalClass.class));
		assertEquals(TransactionalAndOrderedClass.class,
				findAnnotationDeclaringClassForTypes(candidates, TransactionalAndOrderedClass.class));
		assertEquals(TransactionalAndOrderedClass.class,
				findAnnotationDeclaringClassForTypes(candidates, SubTransactionalAndOrderedClass.class));
	}

	@Test
	public void isAnnotationDeclaredLocallyForAllScenarios() throws Exception {
		// no class-level annotation
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, NonAnnotatedInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertTrue(isAnnotationDeclaredLocally(Transactional.class, InheritedAnnotationInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, SubInheritedAnnotationInterface.class));
		assertTrue(isAnnotationDeclaredLocally(Transactional.class, InheritedAnnotationClass.class));
		assertFalse(isAnnotationDeclaredLocally(Transactional.class, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited
		assertTrue(isAnnotationDeclaredLocally(Order.class, NonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationDeclaredLocally(Order.class, SubNonInheritedAnnotationInterface.class));
		assertTrue(isAnnotationDeclaredLocally(Order.class, NonInheritedAnnotationClass.class));
		assertFalse(isAnnotationDeclaredLocally(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void isAnnotationInheritedForAllScenarios() {
		// no class-level annotation
		assertFalse(isAnnotationInherited(Transactional.class, NonAnnotatedInterface.class));
		assertFalse(isAnnotationInherited(Transactional.class, NonAnnotatedClass.class));

		// inherited class-level annotation; note: @Transactional is inherited
		assertFalse(isAnnotationInherited(Transactional.class, InheritedAnnotationInterface.class));
		// isAnnotationInherited() does not currently traverse interface hierarchies.
		// Thus the following, though perhaps counter intuitive, must be false:
		//接口
		assertFalse(isAnnotationInherited(Transactional.class, SubInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Transactional.class, InheritedAnnotationClass.class));
		// 子类 且 父类有可继承注解
		assertTrue(isAnnotationInherited(Transactional.class, SubInheritedAnnotationClass.class));

		// non-inherited class-level annotation; note: @Order is not inherited
		assertFalse(isAnnotationInherited(Order.class, NonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Order.class, SubNonInheritedAnnotationInterface.class));
		assertFalse(isAnnotationInherited(Order.class, NonInheritedAnnotationClass.class));
		assertFalse(isAnnotationInherited(Order.class, SubNonInheritedAnnotationClass.class));
	}

	@Test
	public void getAnnotationAttributesWithoutAttributeAliases() {
		Component component = WebController.class.getAnnotation(Component.class);
		assertNotNull(component);

		AnnotationAttributes attributes = (AnnotationAttributes) getAnnotationAttributes(component);
		assertNotNull(attributes);
		assertEquals("value attribute: ", "webController", attributes.getString(VALUE));
		assertEquals(Component.class, attributes.annotationType());
	}

	/**
	 * 获取有嵌套注解的属性
	 */
	@Test
	public void getAnnotationAttributesWithNestedAnnotations() {
		ComponentScan componentScan = ComponentScanClass.class.getAnnotation(ComponentScan.class);
		assertNotNull(componentScan);

		AnnotationAttributes attributes = getAnnotationAttributes(ComponentScanClass.class, componentScan);
		assertNotNull(attributes);
		assertEquals(ComponentScan.class, attributes.annotationType());

		Filter[] filters = attributes.getAnnotationArray("excludeFilters", Filter.class);
		assertNotNull(filters);

		List<String> patterns = stream(filters).map(Filter::pattern).collect(toList());
		assertEquals(asList("*Foo", "*Bar"), patterns);
	}

	@Test
	public void getAnnotationAttributesWithAttributeAliases() throws Exception {
		//从class 类获取方法对象
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		//获取方法上的注解
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		//获取注解属性
		AnnotationAttributes attributes = (AnnotationAttributes) getAnnotationAttributes(webMapping);
		assertNotNull(attributes);
		assertEquals(WebMapping.class, attributes.annotationType());
		assertEquals("name attribute: ", "foo", attributes.getString("name"));
		assertArrayEquals("value attribute: ", asArray("/test"), attributes.getStringArray(VALUE));
		assertArrayEquals("path attribute: ", asArray("/test"), attributes.getStringArray("path"));

		method = WebController.class.getMethod("handleMappedWithPathAttribute");
		webMapping = method.getAnnotation(WebMapping.class);
		attributes = (AnnotationAttributes) getAnnotationAttributes(webMapping);
		assertNotNull(attributes);
		assertEquals(WebMapping.class, attributes.annotationType());
		assertEquals("name attribute: ", "bar", attributes.getString("name"));
		assertArrayEquals("value attribute: ", asArray("/test"), attributes.getStringArray(VALUE));
		assertArrayEquals("path attribute: ", asArray("/test"), attributes.getStringArray("path"));
	}

	@Test
	public void getAnnotationAttributesWithAttributeAliasesWithDifferentValues() throws Exception {
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(containsString("attribute 'value' and its alias 'path'"));
		exception.expectMessage(containsString("values of [{/enigma}] and [{/test}]"));

		Method method = WebController.class.getMethod("handleMappedWithDifferentPathAndValueAttributes");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		getAnnotationAttributes(webMapping);
	}

	@Test
	public void getValueFromAnnotation() throws Exception {
		Method method = SimpleFoo.class.getMethod("something", Object.class);
		Order order = findAnnotation(method, Order.class);

		assertEquals(1, getValue(order, VALUE));
		assertEquals(1, getValue(order));
	}

	/**
	 * no public 注解，没有理解 todo
	 * @throws Exception
	 */
	@Test
	public void getValueFromNonPublicAnnotation() throws Exception {
		Annotation[] declaredAnnotations = NonPublicAnnotatedClass.class.getDeclaredAnnotations();
		assertEquals(1, declaredAnnotations.length);
		Annotation annotation = declaredAnnotations[0];
		assertNotNull(annotation);
		assertEquals("NonPublicAnnotation", annotation.annotationType().getSimpleName());
		assertEquals(42, getValue(annotation, VALUE));
		assertEquals(42, getValue(annotation));
	}

	@Test
	public void getDefaultValueFromAnnotation() throws Exception {
		Method method = SimpleFoo.class.getMethod("something", Object.class);
		Order order = findAnnotation(method, Order.class);

		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(order, VALUE));
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(order));
	}

	@Test
	public void getDefaultValueFromNonPublicAnnotation() {
		Annotation[] declaredAnnotations = NonPublicAnnotatedClass.class.getDeclaredAnnotations();
		assertEquals(1, declaredAnnotations.length);
		Annotation annotation = declaredAnnotations[0];
		assertNotNull(annotation);
		assertEquals("NonPublicAnnotation", annotation.annotationType().getSimpleName());
		assertEquals(-1, getDefaultValue(annotation, VALUE));
		assertEquals(-1, getDefaultValue(annotation));
	}

	@Test
	public void getDefaultValueFromAnnotationType() {
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(Order.class, VALUE));
		assertEquals(Ordered.LOWEST_PRECEDENCE, getDefaultValue(Order.class));
	}

	@Test
	public void findRepeatableAnnotationOnComposedAnnotation() {
		Repeatable repeatable = findAnnotation(MyRepeatableMeta1.class, Repeatable.class);
		assertNotNull(repeatable);
		assertEquals(MyRepeatableContainer.class, repeatable.value());
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnMethod() throws Exception {
		//
		Method method = InterfaceWithRepeated.class.getMethod("foo");
		Set<MyRepeatable> annotations = getRepeatableAnnotations(method, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(annotations);
		List<String> values = annotations.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(asList("A", "B", "C", "meta1")));
	}

	/**
	 * 测试 @AliasFor 别名对配置,不可缺失
	 * @throws Exception
	 */
	@Test
	public void getRepeatableAnnotationsDeclaredOnClassWithMissingAttributeAliasDeclaration() throws Exception {
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Attribute 'value' in"));
		exception.expectMessage(containsString(BrokenContextConfig.class.getName()));
		exception.expectMessage(containsString("@AliasFor [location]"));

		getRepeatableAnnotations(BrokenConfigHierarchyTestCase.class, BrokenContextConfig.class, BrokenHierarchy.class);
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnClassWithAttributeAliases() {
		final List<String> expectedLocations = asList("A", "B");
		//无containerAnnotation，无法查询到
		Set<ContextConfig> annotations = getRepeatableAnnotations(ConfigHierarchyTestCase.class, ContextConfig.class, null);
		assertNotNull(annotations);
		assertEquals("size if container type is omitted: ", 0, annotations.size());

		annotations = getRepeatableAnnotations(ConfigHierarchyTestCase.class, ContextConfig.class, Hierarchy.class);
		assertNotNull(annotations);
		System.out.println(annotations.size());
		List<String> locations = annotations.stream().map(ContextConfig::location).collect(toList());
		assertThat(locations, is(expectedLocations));

		List<String> values = annotations.stream().map(ContextConfig::value).collect(toList());
		assertThat(values, is(expectedLocations));
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnClass() {
		final List<String> expectedValuesJava = asList("A", "B", "C");
		final List<String> expectedValuesSpring = asList("A", "B", "C", "meta1");

		// Java 8
		MyRepeatable[] array = MyRepeatableClass.class.getAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		List<String> values = stream(array).map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesJava));

		// Spring
		Set<MyRepeatable> set = getRepeatableAnnotations(MyRepeatableClass.class, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));

		// When container type is omitted and therefore inferred from @Repeatable
		set = getRepeatableAnnotations(MyRepeatableClass.class, MyRepeatable.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnSuperclass() {
		final Class<?> clazz = SubMyRepeatableClass.class;
		final List<String> expectedValuesJava = asList("A", "B", "C");
		final List<String> expectedValuesSpring = asList("A", "B", "C", "meta1");

		// Java 8
		MyRepeatable[] array = clazz.getAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		List<String> values = stream(array).map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesJava));

		// Spring
		Set<MyRepeatable> set = getRepeatableAnnotations(clazz, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));

		// When container type is omitted and therefore inferred from @Repeatable
		set = getRepeatableAnnotations(clazz, MyRepeatable.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnClassAndSuperclass() {
		final Class<?> clazz = SubMyRepeatableWithAdditionalLocalDeclarationsClass.class;
		final List<String> expectedValuesJava = asList("X", "Y", "Z");
		final List<String> expectedValuesSpring = asList("X", "Y", "Z", "meta2");

		// Java 8 如果在指定类能查找到，即便注解是被@Inherited 修饰，也不会向上寻找
		MyRepeatable[] array = clazz.getAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		List<String> values = stream(array).map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesJava));

		// Spring  如果在指定类能查找，不会向上寻找
		Set<MyRepeatable> set = getRepeatableAnnotations(clazz, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));

		// When container type is omitted and therefore inferred from @Repeatable
		set = getRepeatableAnnotations(clazz, MyRepeatable.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));
	}

	@Test
	public void getRepeatableAnnotationsDeclaredOnMultipleSuperclasses() {
		final Class<?> clazz = SubSubMyRepeatableWithAdditionalLocalDeclarationsClass.class;
		final List<String> expectedValuesJava = asList("X", "Y", "Z");
		final List<String> expectedValuesSpring = asList("X", "Y", "Z", "meta2");

		// Java 8 如果在指定类能查找到，即便注解是被@Inherited 修饰，也不会向上寻找
		MyRepeatable[] array = clazz.getAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		List<String> values = stream(array).map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesJava));

		// Spring 如果在指定类能查找，不会向上寻找
		Set<MyRepeatable> set = getRepeatableAnnotations(clazz, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));

		//如果容器类未执行，在指定注解上通过@Repeatable 推断容器注解
		// When container type is omitted and therefore inferred from @Repeatable
		set = getRepeatableAnnotations(clazz, MyRepeatable.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));
	}

	@Test
	public void getDeclaredRepeatableAnnotationsDeclaredOnClass() {
		final List<String> expectedValuesJava = asList("A", "B", "C");
		final List<String> expectedValuesSpring = asList("A", "B", "C", "meta1");

		// Java 8 只在直接本类声明的注解上寻找
		MyRepeatable[] array = MyRepeatableClass.class.getDeclaredAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		List<String> values = stream(array).map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesJava));

		// Spring  也是在本类上寻找，但是会递归查找非 指定注解和容器注解
		Set<MyRepeatable> set = getDeclaredRepeatableAnnotations(MyRepeatableClass.class, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));

		// When container type is omitted and therefore inferred from @Repeatable
		set = getDeclaredRepeatableAnnotations(MyRepeatableClass.class, MyRepeatable.class);
		assertNotNull(set);
		values = set.stream().map(MyRepeatable::value).collect(toList());
		assertThat(values, is(expectedValuesSpring));
	}

	@Test
	public void getDeclaredRepeatableAnnotationsDeclaredOnSuperclass() {
		final Class<?> clazz = SubMyRepeatableClass.class;

		// Java 8getDeclaredAnnotationsByType 不会向上查找父类
		MyRepeatable[] array = clazz.getDeclaredAnnotationsByType(MyRepeatable.class);
		assertNotNull(array);
		assertThat(array.length, is(0));

		// Spring  只在本类上寻找
		Set<MyRepeatable> set = getDeclaredRepeatableAnnotations(clazz, MyRepeatable.class, MyRepeatableContainer.class);
		assertNotNull(set);
		assertThat(set.size(), is(0));

		// When container type is omitted and therefore inferred from @Repeatable
		set = getDeclaredRepeatableAnnotations(clazz, MyRepeatable.class);
		assertNotNull(set);
		assertThat(set.size(), is(0));
	}

	@Test
	public void getAttributeOverrideNameFromWrongTargetAnnotation() throws Exception {
		Method attribute = AliasedComposedContextConfig.class.getDeclaredMethod("xmlConfigFile");
		assertThat("xmlConfigFile is not an alias for @Component.",
				getAttributeOverrideName(attribute, ContextConfig.class), is(nullValue()));
	}

	/**
	 * 在没有被别名的属性上序号被覆盖的属性名
	 * @throws Exception
	 */
	@Test
	public void getAttributeOverrideNameForNonAliasedAttribute() throws Exception {

		Method nonAliasedAttribute = ImplicitAliasesContextConfig.class.getDeclaredMethod("nonAliasedAttribute");
		assertThat(getAttributeOverrideName(nonAliasedAttribute, ContextConfig.class), is(nullValue()));
	}

	@Test
	public void getAttributeOverrideNameFromAliasedComposedAnnotation() throws Exception {
		Method attribute = AliasedComposedContextConfig.class.getDeclaredMethod("xmlConfigFile");
		assertEquals("location", getAttributeOverrideName(attribute, ContextConfig.class));
	}

	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithImplicitAliases() throws Exception {
		Method xmlFile = ImplicitAliasesContextConfig.class.getDeclaredMethod("xmlFile");
		Method groovyScript = ImplicitAliasesContextConfig.class.getDeclaredMethod("groovyScript");
		Method value = ImplicitAliasesContextConfig.class.getDeclaredMethod("value");
		Method location1 = ImplicitAliasesContextConfig.class.getDeclaredMethod("location1");
		Method location2 = ImplicitAliasesContextConfig.class.getDeclaredMethod("location2");
		Method location3 = ImplicitAliasesContextConfig.class.getDeclaredMethod("location3");

		// Meta-annotation attribute overrides
		assertEquals("location", getAttributeOverrideName(xmlFile, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(groovyScript, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(value, ContextConfig.class));

		// Implicit aliases 隐式别名
		assertThat(getAttributeAliasNames(xmlFile), containsInAnyOrder("value", "groovyScript", "location1", "location2", "location3"));
		assertThat(getAttributeAliasNames(groovyScript), containsInAnyOrder("value", "xmlFile", "location1", "location2", "location3"));
		assertThat(getAttributeAliasNames(value), containsInAnyOrder("xmlFile", "groovyScript", "location1", "location2", "location3"));
		assertThat(getAttributeAliasNames(location1), containsInAnyOrder("xmlFile", "groovyScript", "value", "location2", "location3"));
		assertThat(getAttributeAliasNames(location2), containsInAnyOrder("xmlFile", "groovyScript", "value", "location1", "location3"));
		assertThat(getAttributeAliasNames(location3), containsInAnyOrder("xmlFile", "groovyScript", "value", "location1", "location2"));
	}

	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithImplicitAliasesForAliasPair() throws Exception {
		Method xmlFile = ImplicitAliasesForAliasPairContextConfig.class.getDeclaredMethod("xmlFile");
		Method groovyScript = ImplicitAliasesForAliasPairContextConfig.class.getDeclaredMethod("groovyScript");

		// Meta-annotation attribute overrides
		assertEquals("location", getAttributeOverrideName(xmlFile, ContextConfig.class));
		assertEquals("value", getAttributeOverrideName(groovyScript, ContextConfig.class));

		// Implicit aliases
		assertThat(getAttributeAliasNames(xmlFile), containsInAnyOrder("groovyScript"));
		assertThat(getAttributeAliasNames(groovyScript), containsInAnyOrder("xmlFile"));
	}

	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithImplicitAliasesWithImpliedAliasNamesOmitted()
			throws Exception {

		Method value = ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class.getDeclaredMethod("value");
		Method location = ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class.getDeclaredMethod("location");
		Method xmlFile = ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class.getDeclaredMethod("xmlFile");

		// Meta-annotation attribute overrides,@AliasFor 的attribute ，value 没有设置时，默认别名该属性方法名
		assertEquals("value", getAttributeOverrideName(value, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(location, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(xmlFile, ContextConfig.class));

		// Implicit aliases
		assertThat(getAttributeAliasNames(value), containsInAnyOrder("location", "xmlFile"));
		assertThat(getAttributeAliasNames(location), containsInAnyOrder("value", "xmlFile"));
		assertThat(getAttributeAliasNames(xmlFile), containsInAnyOrder("value", "location"));
	}

	/**
	 * 可传递的  隐式别名
	 * @throws Exception
	 */
	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithTransitiveImplicitAliases() throws Exception {
		Method xml = TransitiveImplicitAliasesContextConfig.class.getDeclaredMethod("xml");
		Method groovy = TransitiveImplicitAliasesContextConfig.class.getDeclaredMethod("groovy");

		// Explicit meta-annotation attribute overrides
		assertEquals("xmlFile", getAttributeOverrideName(xml, ImplicitAliasesContextConfig.class));
		assertEquals("groovyScript", getAttributeOverrideName(groovy, ImplicitAliasesContextConfig.class));

		// Transitive meta-annotation attribute overrides 传递性
		assertEquals("location", getAttributeOverrideName(xml, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(groovy, ContextConfig.class));

		// Transitive implicit aliases
		assertThat(getAttributeAliasNames(xml), containsInAnyOrder("groovy"));
		assertThat(getAttributeAliasNames(groovy), containsInAnyOrder("xml"));
	}

	/**
	 * 从具有别名对的传递隐式别名的组合注释中获取属性别名
	 * @throws Exception
	 */
	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithTransitiveImplicitAliasesForAliasPair() throws Exception {
		Method xml = TransitiveImplicitAliasesForAliasPairContextConfig.class.getDeclaredMethod("xml");
		Method groovy = TransitiveImplicitAliasesForAliasPairContextConfig.class.getDeclaredMethod("groovy");

		// Explicit meta-annotation attribute overrides
		assertEquals("xmlFile", getAttributeOverrideName(xml, ImplicitAliasesForAliasPairContextConfig.class));
		assertEquals("groovyScript", getAttributeOverrideName(groovy, ImplicitAliasesForAliasPairContextConfig.class));

		// Transitive implicit aliases
		assertThat(getAttributeAliasNames(xml), containsInAnyOrder("groovy"));
		assertThat(getAttributeAliasNames(groovy), containsInAnyOrder("xml"));
	}

	/**
	 * 从具有传递隐式别名（省略隐式别名）的组合注释中获取属性别名
	 * @throws Exception
	 */
	@Test
	public void getAttributeAliasNamesFromComposedAnnotationWithTransitiveImplicitAliasesWithImpliedAliasNamesOmitted()
			throws Exception {

		Method xml = TransitiveImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class.getDeclaredMethod("xml");
		Method groovy = TransitiveImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class.getDeclaredMethod("groovy");

		// Meta-annotation attribute overrides
		assertEquals("location", getAttributeOverrideName(xml, ContextConfig.class));
		assertEquals("location", getAttributeOverrideName(groovy, ContextConfig.class));

		// Explicit meta-annotation attribute overrides
		assertEquals("xmlFile", getAttributeOverrideName(xml, ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class));
		assertEquals("location", getAttributeOverrideName(groovy, ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class));

		// Transitive implicit aliases
		assertThat(getAttributeAliasNames(groovy), containsInAnyOrder("xml"));
		System.out.println(getAttributeAliasNames(groovy));
		assertThat(getAttributeAliasNames(xml), containsInAnyOrder("groovy"));
		System.out.println(getAttributeAliasNames(xml));
	}

	/**
	 * 合成不带属性别名的注释  synthesize :ˈsɪnθəsaɪz   合成;综合
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithoutAttributeAliases() throws Exception {
		Component component = WebController.class.getAnnotation(Component.class);
		assertNotNull(component);
		Component synthesizedComponent = synthesizeAnnotation(component);
		assertNotNull(synthesizedComponent);
		assertSame(component, synthesizedComponent);
		assertEquals("value attribute: ", "webController", synthesizedComponent.value());
	}

	/**
	 *合成已合成的注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAlreadySynthesizedAnnotation() throws Exception {
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		assertNotNull(webMapping);
		WebMapping synthesizedWebMapping = synthesizeAnnotation(webMapping);
		assertNotSame(webMapping, synthesizedWebMapping);
		WebMapping synthesizedAgainWebMapping = synthesizeAnnotation(synthesizedWebMapping);
		assertThat(synthesizedAgainWebMapping, instanceOf(SynthesizedAnnotation.class));
		assertSame(synthesizedWebMapping, synthesizedAgainWebMapping);

		assertEquals("name attribute: ", "foo", synthesizedAgainWebMapping.name());
		assertArrayEquals("aliased path attribute: ", asArray("/test"), synthesizedAgainWebMapping.path());
		assertArrayEquals("actual value attribute: ", asArray("/test"), synthesizedAgainWebMapping.value());
	}

	/**
	 * 合成缺少属性声明别名注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWhereAliasForIsMissingAttributeDeclaration() throws Exception {
		AliasForWithMissingAttributeDeclaration annotation = AliasForWithMissingAttributeDeclarationClass.class.getAnnotation(AliasForWithMissingAttributeDeclaration.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("@AliasFor declaration on attribute 'foo' in annotation"));
		exception.expectMessage(containsString(AliasForWithMissingAttributeDeclaration.class.getName()));
		exception.expectMessage(containsString("points to itself"));
		synthesizeAnnotation(annotation);
	}

	/**
	 *  @AliasFor(value = "bar", attribute = "baz") 属性对别名，value和attribute值不能同时设置
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWhereAliasForHasDuplicateAttributeDeclaration() throws Exception {
		AliasForWithDuplicateAttributeDeclaration annotation = AliasForWithDuplicateAttributeDeclarationClass.class.getAnnotation(AliasForWithDuplicateAttributeDeclaration.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("In @AliasFor declared on attribute 'foo' in annotation"));
		exception.expectMessage(containsString(AliasForWithDuplicateAttributeDeclaration.class.getName()));
		exception.expectMessage(containsString("attribute 'attribute' and its alias 'value' are present with values of [baz] and [bar]"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 将注释与不存在属性的属性别名合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasForNonexistentAttribute() throws Exception {
		AliasForNonexistentAttribute annotation = AliasForNonexistentAttributeClass.class.getAnnotation(AliasForNonexistentAttribute.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Attribute 'foo' in"));
		exception.expectMessage(containsString(AliasForNonexistentAttribute.class.getName()));
		exception.expectMessage(containsString("is declared as an @AliasFor nonexistent attribute 'bar'"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 使用属性别名合成注释，而不带镜像别名
	 * 配置属性别名对时，镜像别名（镜像别名） 的属性设置不为空
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasWithoutMirroredAliasFor() throws Exception {
		AliasForWithoutMirroredAliasFor annotation =
				AliasForWithoutMirroredAliasForClass.class.getAnnotation(AliasForWithoutMirroredAliasFor.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Attribute 'bar' in"));
		exception.expectMessage(containsString(AliasForWithoutMirroredAliasFor.class.getName()));
		exception.expectMessage(containsString("@AliasFor [foo]"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 将带有属性别名的注释与错误属性的镜像别名合成
	 * 镜像别名的属性名如果和源属性名不同，不能互为别名
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasWithMirroredAliasForWrongAttribute() throws Exception {
		AliasForWithMirroredAliasForWrongAttribute annotation =
				AliasForWithMirroredAliasForWrongAttributeClass.class.getAnnotation(AliasForWithMirroredAliasForWrongAttribute.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Attribute 'bar' in"));
		exception.expectMessage(containsString(AliasForWithMirroredAliasForWrongAttribute.class.getName()));
		exception.expectMessage(either(containsString("must be declared as an @AliasFor [foo], not [quux]")).
				or(containsString("is declared as an @AliasFor nonexistent attribute 'quux'")));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 将注释与不同类型的属性的属性别名合成
	 * 互为别名对，属性方法返回值应相同
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasForAttributeOfDifferentType() throws Exception {
		AliasForAttributeOfDifferentType annotation =
				AliasForAttributeOfDifferentTypeClass.class.getAnnotation(AliasForAttributeOfDifferentType.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForAttributeOfDifferentType.class.getName()));
		exception.expectMessage(containsString("attribute 'foo'"));
		exception.expectMessage(containsString("attribute 'bar'"));
		exception.expectMessage(containsString("same return type"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 将注释与缺少默认值的属性合成
	 *
	 *两默认值不为空
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasForWithMissingDefaultValues() throws Exception {
		AliasForWithMissingDefaultValues annotation =
				AliasForWithMissingDefaultValuesClass.class.getAnnotation(AliasForWithMissingDefaultValues.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForWithMissingDefaultValues.class.getName()));
		exception.expectMessage(containsString("attribute 'foo' in annotation"));
		exception.expectMessage(containsString("attribute 'bar' in annotation"));
		exception.expectMessage(containsString("default values"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 为具有不同默认值的属性合成具有属性别名的注释
	 *
	 * 互为别名对的合成注解，默认值应相同
	 *
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasForAttributeWithDifferentDefaultValue() throws Exception {
		AliasForAttributeWithDifferentDefaultValue annotation =
				AliasForAttributeWithDifferentDefaultValueClass.class.getAnnotation(AliasForAttributeWithDifferentDefaultValue.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases"));
		exception.expectMessage(containsString(AliasForAttributeWithDifferentDefaultValue.class.getName()));
		exception.expectMessage(containsString("attribute 'foo' in annotation"));
		exception.expectMessage(containsString("attribute 'bar' in annotation"));
		exception.expectMessage(containsString("same default value"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 元注解属性别名
	 * 将注释与属性别名合成为不存在的元注释
	 * 配置错误，被注解的元注解不存在
	 * @Retention(RetentionPolicy.RUNTIME)
	 * @interface AliasedComposedContextConfigNotMetaPresent {
	 *      //引用的元注释 必须存在于声明 @AliasFor的注释类上
	 *      @AliasFor(annotation = ContextConfig.class, attribute = "location")
	 * 		String xmlConfigFile();
	 *  }
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasForMetaAnnotationThatIsNotMetaPresent() throws Exception {
		AliasedComposedContextConfigNotMetaPresent annotation =
				AliasedComposedContextConfigNotMetaPresentClass.class.getAnnotation(AliasedComposedContextConfigNotMetaPresent.class);
		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("@AliasFor declaration on attribute 'xmlConfigFile' in annotation"));
		exception.expectMessage(containsString(AliasedComposedContextConfigNotMetaPresent.class.getName()));
		exception.expectMessage(containsString("declares an alias for attribute 'location' in meta-annotation"));
		exception.expectMessage(containsString(ContextConfig.class.getName()));
		exception.expectMessage(containsString("not meta-present"));
		synthesizeAnnotation(annotation);
	}

	/**
	 * 使用属性别名合成注记
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliases() throws Exception {
		Method method = WebController.class.getMethod("handleMappedWithValueAttribute");
		WebMapping webMapping = method.getAnnotation(WebMapping.class);
		assertNotNull(webMapping);

		WebMapping synthesizedWebMapping1 = synthesizeAnnotation(webMapping);
		assertThat(synthesizedWebMapping1, instanceOf(SynthesizedAnnotation.class));
		assertNotSame(webMapping, synthesizedWebMapping1);

		assertEquals("name attribute: ", "foo", synthesizedWebMapping1.name());
		assertArrayEquals("aliased path attribute: ", asArray("/test"), synthesizedWebMapping1.path());
		assertArrayEquals("actual value attribute: ", asArray("/test"), synthesizedWebMapping1.value());

		WebMapping synthesizedWebMapping2 = synthesizeAnnotation(webMapping);
		assertThat(synthesizedWebMapping2, instanceOf(SynthesizedAnnotation.class));
		assertNotSame(webMapping, synthesizedWebMapping2);

		assertEquals("name attribute: ", "foo", synthesizedWebMapping2.name());
		assertArrayEquals("aliased path attribute: ", asArray("/test"), synthesizedWebMapping2.path());
		assertArrayEquals("actual value attribute: ", asArray("/test"), synthesizedWebMapping2.value());
	}

	/**
	 * 使用隐式别名合成注解
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliases() throws Exception {
		assertAnnotationSynthesisWithImplicitAliases(ValueImplicitAliasesContextConfigClass.class, "value");
		assertAnnotationSynthesisWithImplicitAliases(Location1ImplicitAliasesContextConfigClass.class, "location1");
		assertAnnotationSynthesisWithImplicitAliases(XmlImplicitAliasesContextConfigClass.class, "xmlFile");
		assertAnnotationSynthesisWithImplicitAliases(GroovyImplicitAliasesContextConfigClass.class, "groovyScript");
	}

	private void assertAnnotationSynthesisWithImplicitAliases(Class<?> clazz, String expected) throws Exception {
		ImplicitAliasesContextConfig config = clazz.getAnnotation(ImplicitAliasesContextConfig.class);
		assertNotNull(config);

		ImplicitAliasesContextConfig synthesizedConfig = synthesizeAnnotation(config);
		assertThat(synthesizedConfig, instanceOf(SynthesizedAnnotation.class));

		assertEquals("value: ", expected, synthesizedConfig.value());
		assertEquals("location1: ", expected, synthesizedConfig.location1());
		assertEquals("xmlFile: ", expected, synthesizedConfig.xmlFile());
		assertEquals("groovyScript: ", expected, synthesizedConfig.groovyScript());
	}

	/**
	 * 使用隐式别名合成注释，省略隐式别名
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliasesWithImpliedAliasNamesOmitted() throws Exception {
		assertAnnotationSynthesisWithImplicitAliasesWithImpliedAliasNamesOmitted(
				ValueImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass.class, "value");
		assertAnnotationSynthesisWithImplicitAliasesWithImpliedAliasNamesOmitted(
				LocationsImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass.class, "location");
		assertAnnotationSynthesisWithImplicitAliasesWithImpliedAliasNamesOmitted(
				XmlFilesImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass.class, "xmlFile");
	}

	private void assertAnnotationSynthesisWithImplicitAliasesWithImpliedAliasNamesOmitted(
			Class<?> clazz, String expected) {

		ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig config = clazz.getAnnotation(
				ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class);
		assertNotNull(config);

		ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig synthesizedConfig = synthesizeAnnotation(config);
		assertThat(synthesizedConfig, instanceOf(SynthesizedAnnotation.class));

		assertEquals("value: ", expected, synthesizedConfig.value());
		assertEquals("locations: ", expected, synthesizedConfig.location());
		assertEquals("xmlFiles: ", expected, synthesizedConfig.xmlFile());
	}

	/**
	 * 使用别名对的隐式别名合成注释
	 * ContextConfig value 和 location 时别名对
	 * @Retention(RetentionPolicy.RUNTIME)
	 * @interface ContextConfig {
	 *
	 *        @AliasFor("location")
	 *        String value() default "";
	 *
	 *        @AliasFor("value")
	 *        String location() default "";
	 *
	 * 		Class<?> klass() default Object.class;
	 *    }
	 *
	 * 所以对元注解别名 xmlFile.AliasFor->ContextConfig.location  groovyScript.AliasFor->ContextConfig.value
	 * @ContextConfig
	 *    @Retention(RetentionPolicy.RUNTIME)
	 *    @interface ImplicitAliasesForAliasPairContextConfig {
	 *
	 *        @AliasFor(annotation = ContextConfig.class, attribute = "location")
	 * 		String xmlFile() default "";
	 *
	 *        @AliasFor(annotation = ContextConfig.class, value = "value")
	 * 		String groovyScript() default "";
	 *    }
	 *
	 * 别名传递性，xmlFile  groovyScript 构成隐式别名
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliasesForAliasPair() throws Exception {
		Class<?> clazz = ImplicitAliasesForAliasPairContextConfigClass.class;
		ImplicitAliasesForAliasPairContextConfig config = clazz.getAnnotation(ImplicitAliasesForAliasPairContextConfig.class);
		assertNotNull(config);

		ImplicitAliasesForAliasPairContextConfig synthesizedConfig = synthesizeAnnotation(config);
		assertThat(synthesizedConfig, instanceOf(SynthesizedAnnotation.class));

		assertEquals("xmlFile: ", "test.xml", synthesizedConfig.xmlFile());
		assertEquals("groovyScript: ", "test.xml", synthesizedConfig.groovyScript());
	}

	/**
	 * 使用传递隐式别名合成注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithTransitiveImplicitAliases() throws Exception {
		Class<?> clazz = TransitiveImplicitAliasesContextConfigClass.class;
		TransitiveImplicitAliasesContextConfig config = clazz.getAnnotation(TransitiveImplicitAliasesContextConfig.class);
		assertNotNull(config);

		TransitiveImplicitAliasesContextConfig synthesizedConfig = synthesizeAnnotation(config);
		assertThat(synthesizedConfig, instanceOf(SynthesizedAnnotation.class));

		assertEquals("xml: ", "test.xml", synthesizedConfig.xml());
		assertEquals("groovy: ", "test.xml", synthesizedConfig.groovy());
	}

	/**
	 * 将注释与别名对的传递隐式别名合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithTransitiveImplicitAliasesForAliasPair() throws Exception {
		Class<?> clazz = TransitiveImplicitAliasesForAliasPairContextConfigClass.class;
		TransitiveImplicitAliasesForAliasPairContextConfig config = clazz.getAnnotation(TransitiveImplicitAliasesForAliasPairContextConfig.class);
		assertNotNull(config);

		TransitiveImplicitAliasesForAliasPairContextConfig synthesizedConfig = synthesizeAnnotation(config);
		assertThat(synthesizedConfig, instanceOf(SynthesizedAnnotation.class));

		assertEquals("xml: ", "test.xml", synthesizedConfig.xml());
		assertEquals("groovy: ", "test.xml", synthesizedConfig.groovy());
	}

	/**
	 * 将注释与缺少默认值的隐式别名合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliasesWithMissingDefaultValues() throws Exception {
		Class<?> clazz = ImplicitAliasesWithMissingDefaultValuesContextConfigClass.class;
		Class<ImplicitAliasesWithMissingDefaultValuesContextConfig> annotationType = ImplicitAliasesWithMissingDefaultValuesContextConfig.class;
		ImplicitAliasesWithMissingDefaultValuesContextConfig config = clazz.getAnnotation(annotationType);
		assertNotNull(config);

		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases:"));
		exception.expectMessage(containsString("attribute 'location1' in annotation [" + annotationType.getName() + "]"));
		exception.expectMessage(containsString("attribute 'location2' in annotation [" + annotationType.getName() + "]"));
		exception.expectMessage(containsString("default values"));

		synthesizeAnnotation(config, clazz);
	}

	/**
	 * 使用具有不同默认值的隐式别名合成注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliasesWithDifferentDefaultValues() throws Exception {
		Class<?> clazz = ImplicitAliasesWithDifferentDefaultValuesContextConfigClass.class;
		Class<ImplicitAliasesWithDifferentDefaultValuesContextConfig> annotationType = ImplicitAliasesWithDifferentDefaultValuesContextConfig.class;
		ImplicitAliasesWithDifferentDefaultValuesContextConfig config = clazz.getAnnotation(annotationType);
		assertNotNull(config);

		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("Misconfigured aliases:"));
		exception.expectMessage(containsString("attribute 'location1' in annotation [" + annotationType.getName() + "]"));
		exception.expectMessage(containsString("attribute 'location2' in annotation [" + annotationType.getName() + "]"));
		exception.expectMessage(containsString("same default value"));

		synthesizeAnnotation(config, clazz);
	}

	/**
	 * 使用具有重复值的隐式别名合成注释,
	 * 互为隐式别名，只能设置一个属性值
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithImplicitAliasesWithDuplicateValues() throws Exception {
		Class<?> clazz = ImplicitAliasesWithDuplicateValuesContextConfigClass.class;
		Class<ImplicitAliasesWithDuplicateValuesContextConfig> annotationType = ImplicitAliasesWithDuplicateValuesContextConfig.class;
		ImplicitAliasesWithDuplicateValuesContextConfig config = clazz.getAnnotation(annotationType);
		assertNotNull(config);

		ImplicitAliasesWithDuplicateValuesContextConfig synthesizedConfig = synthesizeAnnotation(config, clazz);
		assertNotNull(synthesizedConfig);

		exception.expect(AnnotationConfigurationException.class);
		exception.expectMessage(startsWith("In annotation"));
		exception.expectMessage(containsString(annotationType.getName()));
		exception.expectMessage(containsString("declared on class"));
		exception.expectMessage(containsString(clazz.getName()));
		exception.expectMessage(containsString("and synthesized from"));
		exception.expectMessage(either(containsString("attribute 'location1' and its alias 'location2'")).or(
				containsString("attribute 'location2' and its alias 'location1'")));
		exception.expectMessage(either(containsString("are present with values of [1] and [2]")).or(
				containsString("are present with values of [2] and [1]")));

		synthesizedConfig.location1();
	}

	/**
	 * 从自定义的属性map中合成不带属性别名的注记
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromMapWithoutAttributeAliases() throws Exception {
		Component component = WebController.class.getAnnotation(Component.class);
		assertNotNull(component);

		Map<String, Object> map = Collections.singletonMap(VALUE, "webController");
		Component synthesizedComponent = synthesizeAnnotation(map, Component.class, WebController.class);
		assertNotNull(synthesizedComponent);

		assertNotSame(component, synthesizedComponent);
		assertEquals("value from component: ", "webController", component.value());
		assertEquals("value from synthesized component: ", "webController", synthesizedComponent.value());
	}

	/**
	 * 将地图中的注释与嵌套地图合成
	 * @throws Exception
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void synthesizeAnnotationFromMapWithNestedMap() throws Exception {
		ComponentScanSingleFilter componentScan = ComponentScanSingleFilterClass.class.getAnnotation(ComponentScanSingleFilter.class);
		assertNotNull(componentScan);
		assertEquals("value from ComponentScan: ", "*Foo", componentScan.value().pattern());

		AnnotationAttributes attributes = getAnnotationAttributes(
				ComponentScanSingleFilterClass.class, componentScan, false, true);
		assertNotNull(attributes);
		assertEquals(ComponentScanSingleFilter.class, attributes.annotationType());

		Map<String, Object> filterMap = (Map<String, Object>) attributes.get("value");
		assertNotNull(filterMap);
		assertEquals("*Foo", filterMap.get("pattern"));

		// Modify nested map
		filterMap.put("pattern", "newFoo");
		filterMap.put("enigma", 42);

		ComponentScanSingleFilter synthesizedComponentScan = synthesizeAnnotation(
				attributes, ComponentScanSingleFilter.class, ComponentScanSingleFilterClass.class);
		assertNotNull(synthesizedComponentScan);

		assertNotSame(componentScan, synthesizedComponentScan);
		assertEquals("value from synthesized ComponentScan: ", "newFoo", synthesizedComponentScan.value().pattern());
	}

	/**
	 *
	 * @throws Exception
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void synthesizeAnnotationFromMapWithNestedArrayOfMaps() throws Exception {
		ComponentScan componentScan = ComponentScanClass.class.getAnnotation(ComponentScan.class);
		assertNotNull(componentScan);

		AnnotationAttributes attributes = getAnnotationAttributes(ComponentScanClass.class, componentScan, false, true);
		assertNotNull(attributes);
		assertEquals(ComponentScan.class, attributes.annotationType());

		Map<String, Object>[] filters = (Map[]) attributes.get("excludeFilters");
		assertNotNull(filters);

		List<String> patterns = stream(filters).map(m -> (String) m.get("pattern")).collect(toList());
		assertEquals(asList("*Foo", "*Bar"), patterns);

		// Modify nested maps
		filters[0].put("pattern", "newFoo");
		filters[0].put("enigma", 42);
		filters[1].put("pattern", "newBar");
		filters[1].put("enigma", 42);

		ComponentScan synthesizedComponentScan = synthesizeAnnotation(attributes, ComponentScan.class, ComponentScanClass.class);
		assertNotNull(synthesizedComponentScan);

		assertNotSame(componentScan, synthesizedComponentScan);
		patterns = stream(synthesizedComponentScan.excludeFilters()).map(Filter::pattern).collect(toList());
		assertEquals(asList("newFoo", "newBar"), patterns);
	}

	/**
	 * 从没有属性别名的默认值合成注解
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromDefaultsWithoutAttributeAliases() throws Exception {
		AnnotationWithDefaults annotationWithDefaults = synthesizeAnnotation(AnnotationWithDefaults.class);
		assertNotNull(annotationWithDefaults);
		assertEquals("text: ", "enigma", annotationWithDefaults.text());
		assertTrue("predicate: ", annotationWithDefaults.predicate());
		assertArrayEquals("characters: ", new char[] { 'a', 'b', 'c' }, annotationWithDefaults.characters());
	}

	/**
	 * 将默认值中的注释与属性别名合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromDefaultsWithAttributeAliases() throws Exception {
		ContextConfig contextConfig = synthesizeAnnotation(ContextConfig.class);
		assertNotNull(contextConfig);
		assertEquals("value: ", "", contextConfig.value());
		assertEquals("location: ", "", contextConfig.location());
	}

	/**
	 * 使用具有不同值的属性别名合成注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasesWithDifferentValues() throws Exception {
		ContextConfig contextConfig = synthesizeAnnotation(ContextConfigMismatch.class.getAnnotation(ContextConfig.class));
		exception.expect(AnnotationConfigurationException.class);
		getValue(contextConfig);
	}

	/**
	 * 将map中的助兴与属性别名的最小属性合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromMapWithMinimalAttributesWithAttributeAliases() throws Exception {
		Map<String, Object> map = Collections.singletonMap("location", "test.xml");
		ContextConfig contextConfig = synthesizeAnnotation(map, ContextConfig.class, null);
		assertNotNull(contextConfig);
		assertEquals("value: ", "test.xml", contextConfig.value());
		assertEquals("location: ", "test.xml", contextConfig.location());
	}

	/**
	 * 使用属性别名合成来自 map 的注释，这些别名覆盖了具有单个元素的数组
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromMapWithAttributeAliasesThatOverrideArraysWithSingleElements() throws Exception {
		Map<String, Object> map = Collections.singletonMap("value", "/foo");
		Get get = synthesizeAnnotation(map, Get.class, null);
		assertNotNull(get);
		assertEquals("value: ", "/foo", get.value());
		assertEquals("path: ", "/foo", get.path());

		map = Collections.singletonMap("path", "/foo");
		get = synthesizeAnnotation(map, Get.class, null);
		assertNotNull(get);
		assertEquals("value: ", "/foo", get.value());
		assertEquals("path: ", "/foo", get.path());
	}

	/**
	 * 使用隐式属性别名合成map中的注解
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromMapWithImplicitAttributeAliases() throws Exception {
		assertAnnotationSynthesisFromMapWithImplicitAliases("value");
		assertAnnotationSynthesisFromMapWithImplicitAliases("location1");
		assertAnnotationSynthesisFromMapWithImplicitAliases("location2");
		assertAnnotationSynthesisFromMapWithImplicitAliases("location3");
		assertAnnotationSynthesisFromMapWithImplicitAliases("xmlFile");
		assertAnnotationSynthesisFromMapWithImplicitAliases("groovyScript");
	}

	private void assertAnnotationSynthesisFromMapWithImplicitAliases(String attributeNameAndValue) throws Exception {
		Map<String, Object> map = Collections.singletonMap(attributeNameAndValue, attributeNameAndValue);
		ImplicitAliasesContextConfig config = synthesizeAnnotation(map, ImplicitAliasesContextConfig.class, null);
		assertNotNull(config);
		assertEquals("value: ", attributeNameAndValue, config.value());
		assertEquals("location1: ", attributeNameAndValue, config.location1());
		assertEquals("location2: ", attributeNameAndValue, config.location2());
		assertEquals("location3: ", attributeNameAndValue, config.location3());
		assertEquals("xmlFile: ", attributeNameAndValue, config.xmlFile());
		assertEquals("groovyScript: ", attributeNameAndValue, config.groovyScript());
	}

	@Test
	public void synthesizeAnnotationFromMapWithMissingAttributeValue() throws Exception {
		assertMissingTextAttribute(Collections.emptyMap());
	}

	@Test
	public void synthesizeAnnotationFromMapWithNullAttributeValue() throws Exception {
		Map<String, Object> map = Collections.singletonMap("text", null);
		assertTrue(map.containsKey("text"));
		assertMissingTextAttribute(map);
	}

	private void assertMissingTextAttribute(Map<String, Object> attributes) {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(startsWith("Attributes map"));
		exception.expectMessage(containsString("returned null for required attribute 'text'"));
		exception.expectMessage(containsString("defined by annotation type [" + AnnotationWithoutDefaults.class.getName() + "]"));
		synthesizeAnnotation(attributes, AnnotationWithoutDefaults.class, null);
	}

	/**
	 * 从自定义Map属性合成具有不正确类型的属性的注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromMapWithAttributeOfIncorrectType() throws Exception {
		Map<String, Object> map = Collections.singletonMap(VALUE, 42L);

		exception.expect(IllegalArgumentException.class);
		exception.expectMessage(startsWith("Attributes map"));
		exception.expectMessage(containsString("returned a value of type [java.lang.Long]"));
		exception.expectMessage(containsString("for attribute 'value'"));
		exception.expectMessage(containsString("but a value of type [java.lang.String] is required"));
		exception.expectMessage(containsString("as defined by annotation type [" + Component.class.getName() + "]"));

		synthesizeAnnotation(map, Component.class, null);
	}

	/**
	 * 从没有属性别名的注释属性合成注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationFromAnnotationAttributesWithoutAttributeAliases() throws Exception {
		// 1) Get an annotation
		Component component = WebController.class.getAnnotation(Component.class);
		assertNotNull(component);

		// 2) Convert the annotation into AnnotationAttributes
		AnnotationAttributes attributes = getAnnotationAttributes(WebController.class, component);
		assertNotNull(attributes);

		// 3) Synthesize the AnnotationAttributes back into an annotation
		Component synthesizedComponent = synthesizeAnnotation(attributes, Component.class, WebController.class);
		assertNotNull(synthesizedComponent);

		// 4) Verify that the original and synthesized annotations are equivalent
		assertNotSame(component, synthesizedComponent);
		assertEquals(component, synthesizedComponent);
		assertEquals("value from component: ", "webController", component.value());
		assertEquals("value from synthesized component: ", "webController", synthesizedComponent.value());
	}

	/**
	 * 到合成批注的字符串
	 * @throws Exception
	 */
	@Test
	public void toStringForSynthesizedAnnotations() throws Exception {
		Method methodWithPath = WebController.class.getMethod("handleMappedWithPathAttribute");
		WebMapping webMappingWithAliases = methodWithPath.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithAliases);

		Method methodWithPathAndValue = WebController.class.getMethod("handleMappedWithSamePathAndValueAttributes");
		WebMapping webMappingWithPathAndValue = methodWithPathAndValue.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithPathAndValue);

		WebMapping synthesizedWebMapping1 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping1);
		WebMapping synthesizedWebMapping2 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping2);

		assertThat(webMappingWithAliases.toString(), is(not(synthesizedWebMapping1.toString())));
		assertToStringForWebMappingWithPathAndValue(synthesizedWebMapping1);
		assertToStringForWebMappingWithPathAndValue(synthesizedWebMapping2);
	}

	private void assertToStringForWebMappingWithPathAndValue(WebMapping webMapping) {
		String string = webMapping.toString();
		assertThat(string, startsWith("@" + WebMapping.class.getName() + "("));
		assertThat(string, containsString("value=[/test]"));
		assertThat(string, containsString("path=[/test]"));
		assertThat(string, containsString("name=bar"));
		assertThat(string, containsString("method="));
		assertThat(string, containsString("[GET, POST]"));
		assertThat(string, endsWith(")"));
	}

	@Test
	public void equalsForSynthesizedAnnotations() throws Exception {
		Method methodWithPath = WebController.class.getMethod("handleMappedWithPathAttribute");
		WebMapping webMappingWithAliases = methodWithPath.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithAliases);

		Method methodWithPathAndValue = WebController.class.getMethod("handleMappedWithSamePathAndValueAttributes");
		WebMapping webMappingWithPathAndValue = methodWithPathAndValue.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithPathAndValue);

		WebMapping synthesizedWebMapping1 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping1);
		WebMapping synthesizedWebMapping2 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping2);

		// Equality amongst standard annotations
		assertThat(webMappingWithAliases, is(webMappingWithAliases));
		assertThat(webMappingWithPathAndValue, is(webMappingWithPathAndValue));

		// Inequality amongst standard annotations
		assertThat(webMappingWithAliases, is(not(webMappingWithPathAndValue)));
		assertThat(webMappingWithPathAndValue, is(not(webMappingWithAliases)));

		// Equality amongst synthesized annotations
		assertThat(synthesizedWebMapping1, is(synthesizedWebMapping1));
		assertThat(synthesizedWebMapping2, is(synthesizedWebMapping2));
		assertThat(synthesizedWebMapping1, is(synthesizedWebMapping2));
		assertThat(synthesizedWebMapping2, is(synthesizedWebMapping1));

		// Equality between standard and synthesized annotations
		assertThat(synthesizedWebMapping1, is(webMappingWithPathAndValue));
		assertThat(webMappingWithPathAndValue, is(synthesizedWebMapping1));

		// Inequality between standard and synthesized annotations
		assertThat(synthesizedWebMapping1, is(not(webMappingWithAliases)));
		assertThat(webMappingWithAliases, is(not(synthesizedWebMapping1)));
	}

	@Test
	public void hashCodeForSynthesizedAnnotations() throws Exception {
		Method methodWithPath = WebController.class.getMethod("handleMappedWithPathAttribute");
		WebMapping webMappingWithAliases = methodWithPath.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithAliases);

		Method methodWithPathAndValue = WebController.class.getMethod("handleMappedWithSamePathAndValueAttributes");
		WebMapping webMappingWithPathAndValue = methodWithPathAndValue.getAnnotation(WebMapping.class);
		assertNotNull(webMappingWithPathAndValue);

		WebMapping synthesizedWebMapping1 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping1);
		WebMapping synthesizedWebMapping2 = synthesizeAnnotation(webMappingWithAliases);
		assertNotNull(synthesizedWebMapping2);

		// Equality amongst standard annotations
		assertThat(webMappingWithAliases.hashCode(), is(webMappingWithAliases.hashCode()));
		assertThat(webMappingWithPathAndValue.hashCode(), is(webMappingWithPathAndValue.hashCode()));

		// Inequality amongst standard annotations
		assertThat(webMappingWithAliases.hashCode(), is(not(webMappingWithPathAndValue.hashCode())));
		assertThat(webMappingWithPathAndValue.hashCode(), is(not(webMappingWithAliases.hashCode())));

		// Equality amongst synthesized annotations
		assertThat(synthesizedWebMapping1.hashCode(), is(synthesizedWebMapping1.hashCode()));
		assertThat(synthesizedWebMapping2.hashCode(), is(synthesizedWebMapping2.hashCode()));
		assertThat(synthesizedWebMapping1.hashCode(), is(synthesizedWebMapping2.hashCode()));
		assertThat(synthesizedWebMapping2.hashCode(), is(synthesizedWebMapping1.hashCode()));

		// Equality between standard and synthesized annotations
		assertThat(synthesizedWebMapping1.hashCode(), is(webMappingWithPathAndValue.hashCode()));
		assertThat(webMappingWithPathAndValue.hashCode(), is(synthesizedWebMapping1.hashCode()));

		// Inequality between standard and synthesized annotations
		assertThat(synthesizedWebMapping1.hashCode(), is(not(webMappingWithAliases.hashCode())));
		assertThat(webMappingWithAliases.hashCode(), is(not(synthesizedWebMapping1.hashCode())));
	}

	/**
	 * 完全基于反射的测试，用于验证对跨包合成注释的支持，这些 注释 具有用户类型的非公开可见性（例如，使用 @AliasFor的非公共注释）
	 * Fully reflection-based test that verifies support for
	 * {@linkplain AnnotationUtils#synthesizeAnnotation synthesizing annotations}
	 * across packages with non-public visibility of user types (e.g., a non-public
	 * annotation that uses {@code @AliasFor}).
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void synthesizeNonPublicAnnotationWithAttributeAliasesFromDifferentPackage() throws Exception {
		Class<?> clazz =
				ClassUtils.forName("org.springframework.core.annotation.subpackage.NonPublicAliasedAnnotatedClass", null);
		Class<? extends Annotation> annotationType = (Class<? extends Annotation>)
				ClassUtils.forName("org.springframework.core.annotation.subpackage.NonPublicAliasedAnnotation", null);

		Annotation annotation = clazz.getAnnotation(annotationType);
		assertNotNull(annotation);
		Annotation synthesizedAnnotation = synthesizeAnnotation(annotation);
		assertNotSame(annotation, synthesizedAnnotation);

		assertNotNull(synthesizedAnnotation);
		assertEquals("name attribute: ", "test", getValue(synthesizedAnnotation, "name"));
		assertEquals("aliased path attribute: ", "/test", getValue(synthesizedAnnotation, "path"));
		assertEquals("aliased path attribute: ", "/test", getValue(synthesizedAnnotation, "value"));
	}

	/**
	 * 在嵌套批注中将批注与属性别名合成
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithAttributeAliasesInNestedAnnotations() throws Exception {
		List<String> expectedLocations = asList("A", "B");

		Hierarchy hierarchy = ConfigHierarchyTestCase.class.getAnnotation(Hierarchy.class);
		assertNotNull(hierarchy);
		//在嵌套批注中将批注与属性别名合成，获取到属性值也是合成的
		Hierarchy synthesizedHierarchy = synthesizeAnnotation(hierarchy);
		assertNotSame(hierarchy, synthesizedHierarchy);
		assertThat(synthesizedHierarchy, instanceOf(SynthesizedAnnotation.class));

		ContextConfig[] configs = synthesizedHierarchy.value();
		assertNotNull(configs);
		assertTrue("nested annotations must be synthesized",
				stream(configs).allMatch(c -> c instanceof SynthesizedAnnotation));

		List<String> locations = stream(configs).map(ContextConfig::location).collect(toList());
		assertThat(locations, is(expectedLocations));

		List<String> values = stream(configs).map(ContextConfig::value).collect(toList());
		assertThat(values, is(expectedLocations));
	}

	/**
	 * 将含有注解数组的合成注解
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithArrayOfAnnotations() throws Exception {
		List<String> expectedLocations = asList("A", "B");

		Hierarchy hierarchy = ConfigHierarchyTestCase.class.getAnnotation(Hierarchy.class);
		assertNotNull(hierarchy);
		Hierarchy synthesizedHierarchy = synthesizeAnnotation(hierarchy);
		assertThat(synthesizedHierarchy, instanceOf(SynthesizedAnnotation.class));

		ContextConfig contextConfig = SimpleConfigTestCase.class.getAnnotation(ContextConfig.class);
		assertNotNull(contextConfig);

		ContextConfig[] configs = synthesizedHierarchy.value();
		List<String> locations = stream(configs).map(ContextConfig::location).collect(toList());
		assertThat(locations, is(expectedLocations));

		// Alter array returned from synthesized annotation
		configs[0] = contextConfig;

		// Re-retrieve the array from the synthesized annotation
		configs = synthesizedHierarchy.value();
		List<String> values = stream(configs).map(ContextConfig::value).collect(toList());
		assertThat(values, is(expectedLocations));
	}

	/**
	 * 使用字符数组合成注释
	 * @throws Exception
	 */
	@Test
	public void synthesizeAnnotationWithArrayOfChars() throws Exception {
		CharsContainer charsContainer = GroupOfCharsClass.class.getAnnotation(CharsContainer.class);
		assertNotNull(charsContainer);
		CharsContainer synthesizedCharsContainer = synthesizeAnnotation(charsContainer);
		assertThat(synthesizedCharsContainer, instanceOf(SynthesizedAnnotation.class));

		char[] chars = synthesizedCharsContainer.chars();
		assertArrayEquals(new char[] { 'x', 'y', 'z' }, chars);

		// Alter array returned from synthesized annotation
		chars[0] = '?';

		// Re-retrieve the array from the synthesized annotation
		chars = synthesizedCharsContainer.chars();
		assertArrayEquals(new char[] { 'x', 'y', 'z' }, chars);
	}


	@SafeVarargs
	static <T> T[] asArray(T... arr) {
		return arr;
	}


	@Component("meta1")
	@Order
	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface Meta1 {
	}

	@Component("meta2")
	@Transactional(readOnly = true)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Meta2 {
	}

	@Meta2
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaMeta {
	}

	@MetaMeta
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaMetaMeta {
	}

	@MetaCycle3
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle1 {
	}

	@MetaCycle1
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle2 {
	}

	@MetaCycle2
	@Retention(RetentionPolicy.RUNTIME)
	@interface MetaCycle3 {
	}

	@Meta1
	interface InterfaceWithMetaAnnotation {
	}

	@Meta2
	static class ClassWithLocalMetaAnnotationAndMetaAnnotatedInterface implements InterfaceWithMetaAnnotation {
	}

	@Meta1
	static class ClassWithInheritedMetaAnnotation {
	}

	@Meta2
	static class SubClassWithInheritedMetaAnnotation extends ClassWithInheritedMetaAnnotation {
	}

	static class SubSubClassWithInheritedMetaAnnotation extends SubClassWithInheritedMetaAnnotation {
	}

	@Transactional
	static class ClassWithInheritedAnnotation {
	}

	@Meta2
	static class SubClassWithInheritedAnnotation extends ClassWithInheritedAnnotation {
	}

	static class SubSubClassWithInheritedAnnotation extends SubClassWithInheritedAnnotation {
	}

	@MetaMeta
	static class MetaMetaAnnotatedClass {
	}

	@MetaMetaMeta
	static class MetaMetaMetaAnnotatedClass {
	}

	@MetaCycle3
	static class MetaCycleAnnotatedClass {
	}

	public interface AnnotatedInterface {

		@Order(0)
		void fromInterfaceImplementedByRoot();
	}

	public static class Root implements AnnotatedInterface {

		@Order(27)
		public void annotatedOnRoot() {
		}

		@Meta1
		public void metaAnnotatedOnRoot() {
		}

		public void overrideToAnnotate() {
		}

		@Order(27)
		public void overrideWithoutNewAnnotation() {
		}

		public void notAnnotated() {
		}

		@Override
		public void fromInterfaceImplementedByRoot() {
		}
	}

	public static class Leaf extends Root {

		@Order(25)
		public void annotatedOnLeaf() {
		}

		@Meta1
		public void metaAnnotatedOnLeaf() {
		}

		@MetaMeta
		public void metaMetaAnnotatedOnLeaf() {
		}

		@Override
		@Order(1)
		public void overrideToAnnotate() {
		}

		@Override
		public void overrideWithoutNewAnnotation() {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface Transactional {

		boolean readOnly() default false;
	}

	public static abstract class Foo<T> {

		@Order(1)
		public abstract void something(T arg);
	}

	public static class SimpleFoo extends Foo<String> {

		@Override
		@Transactional
		public void something(final String arg) {
		}
	}

	@Transactional
	public interface InheritedAnnotationInterface {
	}

	public interface SubInheritedAnnotationInterface extends InheritedAnnotationInterface {
	}

	public interface SubSubInheritedAnnotationInterface extends SubInheritedAnnotationInterface {
	}

	@Order
	public interface NonInheritedAnnotationInterface {
	}

	public interface SubNonInheritedAnnotationInterface extends NonInheritedAnnotationInterface {
	}

	public interface SubSubNonInheritedAnnotationInterface extends SubNonInheritedAnnotationInterface {
	}

	public static class NonAnnotatedClass {
	}

	public interface NonAnnotatedInterface {
	}

	@Transactional
	public static class InheritedAnnotationClass {
	}

	public static class SubInheritedAnnotationClass extends InheritedAnnotationClass {
	}

	@Order
	public static class NonInheritedAnnotationClass {
	}

	public static class SubNonInheritedAnnotationClass extends NonInheritedAnnotationClass {
	}

	@Transactional
	public static class TransactionalClass {
	}

	@Order
	public static class TransactionalAndOrderedClass extends TransactionalClass {
	}

	public static class SubTransactionalAndOrderedClass extends TransactionalAndOrderedClass {
	}

	public interface InterfaceWithAnnotatedMethod {

		@Order
		void foo();
	}

	public static class ImplementsInterfaceWithAnnotatedMethod implements InterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	public static class SubOfImplementsInterfaceWithAnnotatedMethod extends ImplementsInterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	public abstract static class AbstractDoesNotImplementInterfaceWithAnnotatedMethod
			implements InterfaceWithAnnotatedMethod {
	}

	public static class SubOfAbstractImplementsInterfaceWithAnnotatedMethod
			extends AbstractDoesNotImplementInterfaceWithAnnotatedMethod {

		@Override
		public void foo() {
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@interface MyRepeatableContainer {

		MyRepeatable[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@Repeatable(MyRepeatableContainer.class)
	@interface MyRepeatable {

		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@MyRepeatable("meta1")
	@interface MyRepeatableMeta1 {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Inherited
	@MyRepeatable("meta2")
	@interface MyRepeatableMeta2 {
	}

	interface InterfaceWithRepeated {

		@MyRepeatable("A")
		@MyRepeatableContainer({@MyRepeatable("B"), @MyRepeatable("C")})
		@MyRepeatableMeta1
		void foo();
	}

	/**
	 * 重复使用注解
	 * 两种方式
	 */
	@MyRepeatable("A")
	@MyRepeatableContainer({@MyRepeatable("B"), @MyRepeatable("C")})
	@MyRepeatableMeta1
	static class MyRepeatableClass {
	}

	static class SubMyRepeatableClass extends MyRepeatableClass {
	}

	@MyRepeatable("X")
	@MyRepeatableContainer({@MyRepeatable("Y"), @MyRepeatable("Z")})
	@MyRepeatableMeta2
	static class SubMyRepeatableWithAdditionalLocalDeclarationsClass extends MyRepeatableClass {
	}

	static class SubSubMyRepeatableWithAdditionalLocalDeclarationsClass extends
			SubMyRepeatableWithAdditionalLocalDeclarationsClass {
	}

	enum RequestMethod {
		GET, POST
	}

	/**
	 * Mock of {@code org.springframework.web.bind.annotation.RequestMapping}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface WebMapping {

		String name();

		@AliasFor("path")
		String[] value() default "";

		@AliasFor(attribute = "value")
		String[] path() default "";

		RequestMethod[] method() default {};
	}

	/**
	 * Mock of {@code org.springframework.web.bind.annotation.GetMapping}, except
	 * that the String arrays are overridden with single String elements.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@WebMapping(method = RequestMethod.GET, name = "")
	@interface Get {

		@AliasFor(annotation = WebMapping.class)
		String value() default "";

		@AliasFor(annotation = WebMapping.class)
		String path() default "";
	}

	/**
	 * Mock of {@code org.springframework.web.bind.annotation.PostMapping}, except
	 * that the path is overridden by convention with single String element.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@WebMapping(method = RequestMethod.POST, name = "")
	@interface Post {

		String path() default "";
	}

	@Component("webController")
	static class WebController {

		@WebMapping(value = "/test", name = "foo")
		public void handleMappedWithValueAttribute() {
		}

		@WebMapping(path = "/test", name = "bar", method = { RequestMethod.GET, RequestMethod.POST })
		public void handleMappedWithPathAttribute() {
		}

		@Get("/test")
		public void getMappedWithValueAttribute() {
		}

		@Get(path = "/test")
		public void getMappedWithPathAttribute() {
		}

		@Post(path = "/test")
		public void postMappedWithPathAttribute() {
		}

		/**
		 * mapping is logically "equal" to handleMappedWithPathAttribute().
		 */
		@WebMapping(value = "/test", path = "/test1", name = "bar", method = { RequestMethod.GET, RequestMethod.POST })
		public void handleMappedWithSamePathAndValueAttributes() {
		}

		@WebMapping(value = "/enigma", path = "/test", name = "baz")
		public void handleMappedWithDifferentPathAndValueAttributes() {
		}
	}

	/**
	 * AliasPair 注解
	 * Mock of {@code org.springframework.test.context.ContextConfiguration}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface ContextConfig {

		@AliasFor("location")
		String value() default "";

		@AliasFor("value")
		String location() default "";

		Class<?> klass() default Object.class;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface BrokenContextConfig {

		// Intentionally missing:
		// @AliasFor("location")
		String value() default "";

		@AliasFor("value")
		String location() default "";
	}

	/**
	 * Mock of {@code org.springframework.test.context.ContextHierarchy}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface Hierarchy {
		ContextConfig[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface BrokenHierarchy {
		BrokenContextConfig[] value();
	}

	@Hierarchy({@ContextConfig("A"), @ContextConfig(location = "B")})
	static class ConfigHierarchyTestCase {
	}

	@BrokenHierarchy(@BrokenContextConfig)
	static class BrokenConfigHierarchyTestCase {
	}

	@ContextConfig("simple.xml")
	static class SimpleConfigTestCase {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface CharsContainer {

		@AliasFor(attribute = "chars")
		char[] value() default {};

		@AliasFor(attribute = "value")
		char[] chars() default {};
	}

	@CharsContainer(chars = { 'x', 'y', 'z' })
	static class GroupOfCharsClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithMissingAttributeDeclaration {

		@AliasFor
		String foo() default "";
	}

	@AliasForWithMissingAttributeDeclaration
	static class AliasForWithMissingAttributeDeclarationClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithDuplicateAttributeDeclaration {

		@AliasFor(value = "bar", attribute = "baz")
		String foo() default "";
	}

	@AliasForWithDuplicateAttributeDeclaration
	static class AliasForWithDuplicateAttributeDeclarationClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForNonexistentAttribute {

		@AliasFor("bar") //value="value"
		String foo() default "";
	}

	@AliasForNonexistentAttribute
	static class AliasForNonexistentAttributeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithoutMirroredAliasFor {

		@AliasFor("bar")
		String foo() default "";

		String bar() default "";
	}

	@AliasForWithoutMirroredAliasFor
	static class AliasForWithoutMirroredAliasForClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithMirroredAliasForWrongAttribute {

		@AliasFor(attribute = "bar")
		String[] foo() default "";

		@AliasFor(attribute = "quux")
		String[] bar() default "";
	}

	@AliasForWithMirroredAliasForWrongAttribute
	static class AliasForWithMirroredAliasForWrongAttributeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForAttributeOfDifferentType {

		@AliasFor("bar")
		String[] foo() default "";

		@AliasFor("foo")
		boolean bar() default true;
	}

	@AliasForAttributeOfDifferentType
	static class AliasForAttributeOfDifferentTypeClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForWithMissingDefaultValues {

		@AliasFor(attribute = "bar")
		String foo();

		@AliasFor(attribute = "foo")
		String bar();
	}

	@AliasForWithMissingDefaultValues(foo = "foo", bar = "bar")
	static class AliasForWithMissingDefaultValuesClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasForAttributeWithDifferentDefaultValue {

		@AliasFor("bar")
		String foo() default "X";

		@AliasFor("foo")
		String bar() default "Z";
	}

	@AliasForAttributeWithDifferentDefaultValue
	static class AliasForAttributeWithDifferentDefaultValueClass {
	}

	// @ContextConfig --> Intentionally NOT meta-present
	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasedComposedContextConfigNotMetaPresent {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xmlConfigFile();
	}

	@AliasedComposedContextConfigNotMetaPresent(xmlConfigFile = "test.xml")
	static class AliasedComposedContextConfigNotMetaPresentClass {
	}

	/**
	 * 元注释中属性的显式别名 @AliasedComposedContextConfig 对ContextConfig的别名
	 */
	//@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface AliasedComposedContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xmlConfigFile();
	}

	/**
	 * ImplicitAliasesContextConfig 的属性都指向注解ContextConfig.location,形成隐式别名
	 */
	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	public @interface ImplicitAliasesContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xmlFile() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String groovyScript() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String value() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location1() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location2() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location3() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "klass")
		Class<?> configClass() default Object.class;

		String nonAliasedAttribute() default "";
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig(groovyScript = "groovyScript",xmlFile = "xmlFile")
	static class GroovyImplicitAliasesContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig(xmlFile = "xmlFile")
	static class XmlImplicitAliasesContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig("value")
	static class ValueImplicitAliasesContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig(location1 = "location1")
	static class Location1ImplicitAliasesContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig(location2 = "location2")
	static class Location2ImplicitAliasesContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesContextConfig(location3 = "location3")
	static class Location3ImplicitAliasesContextConfigClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig {

		// intentionally omitted: attribute = "value"
		@AliasFor(annotation = ContextConfig.class)
		String value() default "";

		// intentionally omitted: attribute = "locations"
		@AliasFor(annotation = ContextConfig.class)
		String location() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xmlFile() default "";
	}

	@ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface TransitiveImplicitAliasesWithImpliedAliasNamesOmittedContextConfig {

		@AliasFor(annotation = ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class, attribute = "xmlFile")
		String xml() default "";

		@AliasFor(annotation = ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig.class, attribute = "location")
		String groovy() default "";
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig("value")
	static class ValueImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig(location = "location")
	static class LocationsImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass {
	}

	// Attribute value intentionally matches attribute name:
	@ImplicitAliasesWithImpliedAliasNamesOmittedContextConfig(xmlFile = "xmlFile")
	static class XmlFilesImplicitAliasesWithImpliedAliasNamesOmittedContextConfigClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface ImplicitAliasesWithMissingDefaultValuesContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location1();

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location2();
	}

	@ImplicitAliasesWithMissingDefaultValuesContextConfig(location1 = "1", location2 = "2")
	static class ImplicitAliasesWithMissingDefaultValuesContextConfigClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface ImplicitAliasesWithDifferentDefaultValuesContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location1() default "foo";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location2() default "bar";
	}

	@ImplicitAliasesWithDifferentDefaultValuesContextConfig(location1 = "1", location2 = "2")
	static class ImplicitAliasesWithDifferentDefaultValuesContextConfigClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface ImplicitAliasesWithDuplicateValuesContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location1() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String location2() default "";
	}

	@ImplicitAliasesWithDuplicateValuesContextConfig(location1 = "1", location2 = "2")
	static class ImplicitAliasesWithDuplicateValuesContextConfigClass {
	}

	@ContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface ImplicitAliasesForAliasPairContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xmlFile() default "";

		@AliasFor(annotation = ContextConfig.class, value = "value")
		String groovyScript() default "";
	}

	@ImplicitAliasesForAliasPairContextConfig(xmlFile = "test.xml")
	static class ImplicitAliasesForAliasPairContextConfigClass {
	}

	@ImplicitAliasesContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface TransitiveImplicitAliasesContextConfig {

		@AliasFor(annotation = ImplicitAliasesContextConfig.class, attribute = "xmlFile")
		String xml() default "";

		@AliasFor(annotation = ImplicitAliasesContextConfig.class, attribute = "groovyScript")
		String groovy() default "";
	}

	@TransitiveImplicitAliasesContextConfig(xml = "test.xml")
	static class TransitiveImplicitAliasesContextConfigClass {
	}

	@ImplicitAliasesForAliasPairContextConfig
	@Retention(RetentionPolicy.RUNTIME)
	@interface TransitiveImplicitAliasesForAliasPairContextConfig {

		@AliasFor(annotation = ContextConfig.class, attribute = "location")
		String xml() default "";

		@AliasFor(annotation = ContextConfig.class, attribute = "value")
		String groovy() default "";
	}

	@TransitiveImplicitAliasesForAliasPairContextConfig(xml = "test.xml")
	static class TransitiveImplicitAliasesForAliasPairContextConfigClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target({})
	@interface Filter {
		String pattern();
	}

	/**
	 * Mock of {@code org.springframework.context.annotation.ComponentScan}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface ComponentScan {
		Filter[] excludeFilters() default {};
	}

	@ComponentScan(excludeFilters = {@Filter(pattern = "*Foo"), @Filter(pattern = "*Bar")})
	static class ComponentScanClass {
	}

	/**
	 * Mock of {@code org.springframework.context.annotation.ComponentScan}.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@interface ComponentScanSingleFilter {
		Filter value();
	}

	@ComponentScanSingleFilter(@Filter(pattern = "*Foo"))
	static class ComponentScanSingleFilterClass {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AnnotationWithDefaults {
		String text() default "enigma";
		boolean predicate() default true;
		char[] characters() default {'a', 'b', 'c'};
	}

	@Retention(RetentionPolicy.RUNTIME)
	@interface AnnotationWithoutDefaults {
		String text();
	}

	@ContextConfig(value = "foo", location = "bar")
	interface ContextConfigMismatch {
	}

}
