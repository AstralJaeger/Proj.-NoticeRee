/*
 * Copyright (c) 2020.
 */

package org.astraljaeger.noticeree.DataTools;

public class Authorizer {

    private static Authorizer instance;

    public static Authorizer getInstance(){
        if(instance == null)
            instance = new Authorizer();
        return instance;
    }

    private Authorizer(){

    }
}
