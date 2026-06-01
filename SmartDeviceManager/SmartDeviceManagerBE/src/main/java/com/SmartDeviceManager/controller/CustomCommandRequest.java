package com.SmartDeviceManager.controller;

public record CustomCommandRequest(
        String name,
        Integer brightness,
        Integer temperature,
        Integer red,
        Integer green,
        Integer blue,
        Boolean colourMode,
        Boolean transitionMode,
        Integer transition) {}
