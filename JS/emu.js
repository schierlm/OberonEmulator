var activeButton=1, interClickButton=0;
var screenCanvas = null;
var screenCtx;
var backBuffer;
var clipboard;

emuInit = function(width, height) {
	let $ = document.getElementById.bind(document);
	let ml = $("mouseL");
	let mm = $("mouseM");
	let mr = $("mouseR");
	let resetButton = $("resetbutton");
	let breakButton = $("breakbutton");
	let clipboardButton = $("clipboardBtn")
	clipboard = $("clipboardText");
	screenCanvas = $("screen");

	breakButton.onclick = function() {emulator.reset(false);};
	resetButton.onclick = function() {emulator.reset(true);};

	var screen = screenCanvas;
	screen.width=width | 0;
	screen.height=height | 0;
	screenCtx = screen.getContext("2d");
	backBuffer = screenCtx.createImageData(screen.width,screen.height);
	clipboardButton.onclick = function() {
		if (clipboard.style.height == "0px") {
			clipboard.style.height = 200;
			clipboard.style.width = width;
		} else {
			clipboard.style.height = 0;
			clipboard.style.width = 0;
		}
	}
	ml.dataBtn = 1;
	mm.dataBtn = 2;
	mr.dataBtn = 3;
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
};
