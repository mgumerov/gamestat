package ru.slicer.carx;

import org.springframework.context.support.FileSystemXmlApplicationContext;

public class App {
    public static void main(final String[] args) {
        FileSystemXmlApplicationContext context = null;
        try {
            context = new FileSystemXmlApplicationContext("classpath:applicationContext.xml");
            context.registerShutdownHook();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}