package com.baichen.rpc.register;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceMateData {

    private String serviceName;

    private String host;

    private int port;
}
