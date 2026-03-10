package com.baichen.rpc.register;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ServiceRegisterConfig {

    private String registerType = "zookeeper";

    private String connectString;
}
