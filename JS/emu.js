var disk = [];
var activeButton=1, interClickButton=0;
var screenCanvas = null;
var screenCtx;
var backBuffer;
var clipboard;

emuInit = function() {
	var img = new Image();
	var params = window.location.hash.substring(1).split(",");
	
	var checkAll = function() {
		if (screenCanvas && disk.length > 0) emulator.reset(true);
	};

	img.onload = function() {
		var c = document.createElement("canvas");
		var w = this.width;
		var h = this.height;
		c.width = w;
		c.height = h;
		var x = c.getContext("2d");
		x.drawImage(this,0,0);
		var d = x.getImageData(0,0,w,h).data;
		for(var i=0; i < h; i++) {
			var r = new Int32Array(w/4);
			for (var j=0; j < w/4; j++) {
				var b = i*4096+j*16+2;
				r[j] = (d[b] & 0xFF) | ((d[b+4] & 0xFF) << 8) | ((d[b+8] & 0xFF) << 16) | ((d[b+12] & 0xFF) << 24);
			}
			disk[i] = r;
		}
		checkAll();
	}
	img.src=params[0]+".png";

	document.getElementById("breakbutton").onclick= function() {emulator.reset(false);};
	document.getElementById("resetbutton").onclick= function() {emulator.reset(true);};
	clipboard = document.getElementById("clipboardText");
	screenCanvas = document.getElementById("screen");
	var screen = screenCanvas;
	screen.width=params[1] | 0;
	screen.height=params[2] | 0;
	screenCtx = screen.getContext("2d");
	backBuffer = screenCtx.createImageData(screen.width,screen.height);
	document.getElementById("clipboardBtn").onclick = function() {
		var cbs = document.getElementById("clipboard").style;
		if(cbs.display == "none") {
			cbs.display="block";
		} else {
			cbs.display="none";
		}
	}
	var ml = document.getElementById("mouseL");ml.dataBtn=1;
	var mm = document.getElementById("mouseM");mm.dataBtn=2;
	var mr = document.getElementById("mouseR");mr.dataBtn=3;
	ml.onmousedown = mm.onmousedown = mr.onmousedown = function(e) {
		e.preventDefault();
		ml.className = mm.className = mr.className = "mousebtn";
		this.className = "mousebtn active";
		activeButton = this.dataBtn;
		interClickButton = 0;
	};
	ml.onmouseup = mm.onmouseup = mr.onmouseup = function(e) {
		if (this.dataBtn != activeButton) {
			interClickButton = this.dataBtn;
			this.className="mousebtn interclick";
		}
	}
	screen.tabIndex = 1000;
	screen.style.outline = "none";
	screen.onkeydown = function(e) {
		if (e.keyCode == 18 && !e.ctrlKey) {
			emulator.registerMouseButton(2, true);
			e.preventDefault();
		} else if (e.keyCode == 8 || e.keyCode == 9 || e.keyCode == 27 || e.keyCode == 13) {
			emulator.registerKey(e.keyCode);
			e.preventDefault();
		} else if (e.keyCode == 112 || e.keyCode == 45) {
			emulator.registerKey(26);
			e.preventDefault();
		}
	};
	screen.onkeyup = function(e) {
		if (e.keyCode == 18 && !e.ctrlKey) {
			emulator.registerMouseButton(2, false);
			e.preventDefault();
		}
	};
	screen.onkeypress = function(e) {
		var charCode = 0;
		if (e.keyCode == 8 || e.keyCode == 9 || e.keyCode == 27 || e.keyCode == 13) {
			charCode = e.keyCode;
		} else if (e.charCode == 0 && (e.keyCode == 112 || e.keyCode == 45)) {
			charCode = 26;
		} else if (e.charCode != 0) {
			charCode = e.charCode;
		}
		if (charCode != 0) {
			emulator.registerKey(charCode|0);
			e.preventDefault();
		}
	};
	screen.onmousemove = function(e) {
		let scrollX = document.body.scrollLeft;
		let scrollY = document.body.scrollTop;
		let x = e.clientX - screen.offsetLeft + scrollX;
		let y = -(e.clientY - screen.offsetTop + scrollY) + screen.height - 1;
		emulator.registerMousePosition(x, y);
	};
	screen.onmousedown = function(e) {
		var button = e.button + 1;
		if (button == 1) button = activeButton;
		emulator.registerMouseButton(button, true);
	};
	screen.onmouseup = function(e) {
		var button = e.button + 1;
		if (button == 1) {
			if (interClickButton != 0) {
				emulator.registerMouseButton(interClickButton, true);
				emulator.registerMouseButton(interClickButton, false);
			}
			button = activeButton;
		}
		emulator.registerMouseButton(button, false);
	};
	screen.oncontextmenu = function(e) {
		e.preventDefault();
		return false;
	}
	screen.focus();
	checkAll();
};
