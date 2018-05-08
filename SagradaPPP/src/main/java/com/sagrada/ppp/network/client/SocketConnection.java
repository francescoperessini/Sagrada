package com.sagrada.ppp.network.client;

import com.sagrada.ppp.controller.RemoteController;
import com.sagrada.ppp.controller.SocketClientController;

import java.io.IOException;

public class SocketConnection implements ConnectionMode {

    public SocketConnection(){
        super();
    }

    @Override
    public RemoteController getController() {
        try {
            System.out.println("creating socket client controller");
            return new SocketClientController();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
