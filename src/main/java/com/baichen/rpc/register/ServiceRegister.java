package com.baichen.rpc.register;

import java.util.List;

public interface ServiceRegister {

    void init(ServiceRegisterConfig config) throws Exception;

    void register(ServiceMateData service) throws Exception;

    List<ServiceMateData> fetchSeviceList(String serviceName) throws Exception;
}
