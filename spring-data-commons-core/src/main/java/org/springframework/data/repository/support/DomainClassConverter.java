/*
 * Copyright 2008-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.repository.support;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.support.RepositoryFactoryInformation;

/**
 * {@link org.springframework.core.convert.converter.Converter} to convert arbitrary input into domain classes managed
 * by Spring Data {@link CrudRepository} s. The implementation uses a {@link ConversionService} in turn to convert the
 * source type into the domain class' id type which is then converted into a domain class object by using a
 * {@link CrudRepository}.
 * 
 * @author Oliver Gierke
 */
public class DomainClassConverter<T extends ConversionService & ConverterRegistry> implements
		ConditionalGenericConverter, ApplicationContextAware {

	private final Map<EntityInformation<?, Serializable>, CrudRepository<?, Serializable>> repositories = new HashMap<EntityInformation<?, Serializable>, CrudRepository<?, Serializable>>();
	private final T conversionService;

	public DomainClassConverter(T conversionService) {
		this.conversionService = conversionService;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.core.convert.converter.GenericConverter#getConvertibleTypes()
	 */
	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(Object.class, Object.class));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.core.convert.converter.GenericConverter#convert(java.lang.Object, org.springframework.core.convert.TypeDescriptor, org.springframework.core.convert.TypeDescriptor)
	 */
	public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {

		EntityInformation<?, Serializable> info = getRepositoryForDomainType(targetType.getType());

		CrudRepository<?, Serializable> repository = repositories.get(info);
		Serializable id = conversionService.convert(source, info.getIdType());
		return repository.findOne(id);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.core.convert.converter.ConditionalGenericConverter#matches(org.springframework.core.convert.TypeDescriptor, org.springframework.core.convert.TypeDescriptor)
	 */
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {

		EntityInformation<?, ?> info = getRepositoryForDomainType(targetType.getType());

		if (info == null) {
			return false;
		}

		return conversionService.canConvert(sourceType.getType(), info.getIdType());
	}

	private EntityInformation<?, Serializable> getRepositoryForDomainType(Class<?> domainType) {

		for (EntityInformation<?, Serializable> information : repositories.keySet()) {

			if (domainType.equals(information.getJavaType())) {
				return information;
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setApplicationContext(ApplicationContext context) {

		Collection<RepositoryFactoryInformation> providers = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
				RepositoryFactoryInformation.class).values();

		for (RepositoryFactoryInformation entry : providers) {

			EntityInformation<Object, Serializable> metadata = entry.getEntityInformation();
			Class<CrudRepository<Object, Serializable>> objectType = entry.getRepositoryInterface();
			CrudRepository<Object, Serializable> repository = BeanFactoryUtils.beanOfType(context, objectType);

			this.repositories.put(metadata, repository);
		}

		this.conversionService.addConverter(this);
	}
}
