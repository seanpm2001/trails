package org.trails.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ognl.Ognl;
import ognl.OgnlException;


public class TrailsDescriptorService implements DescriptorService {
    protected List<Class> types;
    protected Map<Class, IClassDescriptor> descriptors = new HashMap<Class, IClassDescriptor>();
    private List<DescriptorDecorator> decorators = new ArrayList<DescriptorDecorator>();
    private DescriptorFactory descriptorFactory;

    public void init() throws OgnlException {
        descriptors.clear();
        for (Class type : types) {
            IClassDescriptor descriptor = getDescriptorFactory().buildClassDescriptor(type);
            descriptor = applyDecorators(descriptor);
            descriptors.put(type, descriptor);
        }
        // second pass to find children
        markChildClasses();
    }

    /**
     * Have the decorators decorate this descriptor  todo what are decorators for and/or why would I want to call this
     * menthod?
     *
     * @param descriptor
     * @return The resulting descriptor after all decorators are applied
     */
    protected IClassDescriptor applyDecorators(IClassDescriptor descriptor) {
        IClassDescriptor currDescriptor = descriptor;
        for (DescriptorDecorator decorator : getDecorators()) {
            currDescriptor = decorator.decorate(currDescriptor);
        }
        return currDescriptor;
    }

    //todo once Ognl is taken out of this method, it can be cleaned up
    protected void markChildClasses() throws OgnlException {
        List childRelationships = (List) Ognl.getValue("#root.{ propertyDescriptors }", descriptors);
        for (Iterator iter = childRelationships.iterator(); iter.hasNext();) {
            List list = (List) iter.next();
            for (Iterator iterator = list.iterator(); iterator.hasNext();) {
                IPropertyDescriptor propertyDescriptor = (IPropertyDescriptor) iterator.next();
                if (propertyDescriptor.isCollection() && ((CollectionDescriptor) propertyDescriptor).isChildRelationship()) {
                    getClassDescriptor(((CollectionDescriptor) propertyDescriptor).getElementType()).setChild(true);
                }
            }
        }
    }

    public List getAllDescriptors() {
        List<IClassDescriptor> allDescriptors = new ArrayList<IClassDescriptor>(descriptors.values());
        Collections.sort(allDescriptors, new Comparator<IClassDescriptor>() {
            public int compare(IClassDescriptor o1, IClassDescriptor o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });
        return allDescriptors;
    }

    /* (non-Javadoc)
     * @see org.trails.descriptor.IDescriptorFactory#buildClassDescriptor(java.lang.Class)
     */
    public IClassDescriptor getClassDescriptor(Class type) {
        if (type.getName().contains("CGLIB")) {
            return descriptors.get(type.getSuperclass());
        } else {
            return descriptors.get(type);
        }
    }

    public List getTypes() {
        return types;
    }

    /**
     * @param types all the classes this service should describe
     */
    public void setTypes(List<Class> types) {
        this.types = types;
    }

    public List<DescriptorDecorator> getDecorators() {
        return decorators;
    }

    public void setDecorators(List<DescriptorDecorator> decorators) {
        this.decorators = decorators;
    }

    public DescriptorFactory getDescriptorFactory() {
        return descriptorFactory;
    }

    public void setDescriptorFactory(DescriptorFactory descriptorFactory) {
        this.descriptorFactory = descriptorFactory;
    }
}
