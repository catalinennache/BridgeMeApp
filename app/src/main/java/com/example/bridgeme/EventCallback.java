package com.example.bridgeme;

import org.json.JSONObject;

interface EventCallback{
    void trigger(JSONObject data);
}