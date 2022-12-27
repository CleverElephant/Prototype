package com.github.cleverelephant.prototypetest;

import com.github.cleverelephant.prototype.Prototype;

public interface TestPrototype extends Prototype<String>
{
    String a();
    int b();
    boolean c();
    String d();

    default String $d()
    {
        return "abc";
    }
}