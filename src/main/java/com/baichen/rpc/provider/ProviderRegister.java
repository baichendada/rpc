package com.baichen.rpc.provider;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 服务提供者注册类
 * 负责将服务实现类注册到服务注册中心，以便客户端能够发现和调用这些服务
 * 为什么要这个注册类呢？因为在我们的协议中，调用方法是由调用者的服务名称和方法名称来定位的
 * 为了避免调用者误调用接口实现类中其他我们不想要暴漏的方法，我们需要一个注册类来在服务提供类中进行拦截，只有被注册的方法才会被暴漏给调用者
 */
public class ProviderRegister {

    private final Map<String, InvokerInstance<?>> invokerMap = new ConcurrentHashMap<>();

    public <I> void register(Class<I> interfaceClass, I invokerInstance) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Class " + interfaceClass.getName() + " is not an interface");
        }

        if (invokerMap.putIfAbsent(interfaceClass.getName(), new InvokerInstance<>(invokerInstance, interfaceClass)) != null) {
            throw new IllegalArgumentException("Class " + interfaceClass.getName() + " is already registered");
        }
    }

    public InvokerInstance<?> findInvokerInstance(String serviceName) {
        InvokerInstance<?> invokerInstance = invokerMap.get(serviceName);
        if (invokerInstance == null) {
            throw new IllegalArgumentException("Service " + serviceName + " is not registered");
        }
        return invokerInstance;
    }

    public static class InvokerInstance<I> {
        private final I instance;
        // 为什么需要这个接口类而不直接用实例呢？
        // 因为我们需要通过接口类来获取方法信息进行反射调用，如果直接用实例类的话可能会暴漏一些我们不想暴漏的方法
        private final Class<I> interfaceClass;

        public InvokerInstance(I instance, Class<I> interfaceClass) {
            this.instance = instance;
            this.interfaceClass = interfaceClass;
        }

        public Object invoke(String methodName, Class<?>[] paramsClass, Object[] params) throws Exception {
            // 如果直接使用实例.getClass()来获取方法信息的话
            // 可能获取到的类是一个很多接口的实现类，这样就会暴漏很多我们不想暴漏的方法
            // 而调用者可以通过传入methodName来调用这些方法，这样就会导致安全问题
            Method method = interfaceClass.getDeclaredMethod(methodName, paramsClass);
            return method.invoke(instance, params);

        }
    }
}
