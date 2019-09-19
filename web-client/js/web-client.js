$(document).ready(function() {
    "use strict";


    var content = $('#content');
    var input = $('#input');



    window.WebSocket = window.WebSocket || window.MozWebSocket;
    if(!window.WebSocket) {
        content.html($('<p>', {
            text:'Sorry, but your browser doesn\'t support WebSocket.'
        }));
        return;
    }

    var startForm = $("form");
    var startButton = $("#start-button");

    var modalStart = document.getElementById("modal-start");

    startForm.on("submit", startForm, function(e){
        e.preventDefault();
        var name = $("#nameInput").val();
        console.log(name);
        startWebSocket(name);

        modalStart.style.display="none";
        console.log(modalStart);
        return false;
    });
});

// width = 350, height = 350, tile = 5
var width = 350;
var height = 350;
var tile = 5;
var lastDir = "";
function draw(name, color, position) {
    var canvas = document.getElementById("gameArea");
    var ctx = canvas.getContext("2d");
    var x = (position.x * tile + width) % width;
    var y = (position.y * tile + height) % height;
    ctx.fillStyle = color;
    ctx.fillRect(x, y, tile, tile);

}

function addUser(name) {
    var li = document.createElement("li");
    var peopleList = document.getElementById("people-list");
    li.appendChild(document.createTextNode(name));
    peopleList.appendChild(li);
}

function startWebSocket(name) {
    var gameRunning = false;

    var modalStart = document.getElementById("modal-start");
    var status = $('#status');

    var players = [];

    // open connection
    var connection = new WebSocket('ws://127.0.0.1:8080/?playerName=' + name);

    connection.onopen = function() {
        console.log("web socket connected with server");
        status.html("Connected");
    };
    connection.onerror = function(error) {
        content.html($('<p>', {
          text: 'Sorry, but there\'s some problem with your connection or the server is down.'
        }));
    };
    connection.onmessage = function(message) {
        console.log("receive message from server: ");
        console.log(message);

        var msgJson = JSON.parse(message.data);

        switch(msgJson.msgType) {
            case "GameStart":
                var count = 3;
                setInterval(setStart(count), 1000)
                console.log(msgJson.obj);
                break;
            case "PlayerChanged":
                var playersArray = JSON.parse(msgJson.obj);
                playersArray.forEach(player => {
                    var name = player.name;
                    var color = player.color;
                    var position = player.position;
                    var isReady = player.isReady;
                    var canStart = player.canStart;

                    console.log("[" + position.x + ";" + position.y + "]");
                    console.log(player);

                    if(!gameRunning && !players.includes(name)) {
                        addUser(name);
                        players.push(name);
                    }
                    if(canStart) {
                        gameRunning = true;
                    }
                    draw(name, color, position);
                });
                break;
        }
    };

    $("#gameStart").click(function() {
        if(!gameRunning) {
            console.log("Starting game...");
            connection.send(JSON.stringify({ msgType: "PlayerReady", obj: { player: name}}));
        }
    });

    var arrowKeyCode = {37: "LEFT", 38: "UP", 39: "RIGHT", 40: "DOWN"}
    $(document).keydown(function(e) {
        if(gameRunning && [e.keyCode] !== undefined) {
            console.log("keyPressed : " + arrowKeyCode[e.keyCode]);
            connection.send(JSON.stringify({ msgType: "PlayerMoveRequest", obj: {direction: arrowKeyCode[e.keyCode]}}));
            lastDir = arrowKeyCode[e.keyCode];
        }
    });
}

var count = 3;
function setStart() {
    console.log("starting in " + count + " second");
    if(count == 0) {
        console.log("START");
        gameRunning = true;
    }
    else
        count--;
}