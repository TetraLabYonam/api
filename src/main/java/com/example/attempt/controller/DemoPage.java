package com.example.attempt.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DemoPage {
    @GetMapping("/rooms")
    public String roomList(){
        return "room";
    }

    @GetMapping("/rooms/admin")
    public String roomAdmin(){
        return "roomAdmin";
    }

    @GetMapping("/rooms/{roomId}")
    public String page(@PathVariable String roomId, Model m){
        m.addAttribute("roomId", roomId);
        return "room";
    }
}