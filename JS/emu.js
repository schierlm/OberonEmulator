var activeButton=1, interClickButton=0;
var screenCtx;
var backBuffer;

emuInit = function(width, height) {
	let $ = document.getElementById.bind(document);
	let ml = emulator.clickLeft;
	let mm = emulator.clickMiddle;
	let mr = emulator.clickRight;

	emulator.screen.width=width | 0;
	emulator.screen.height=height | 0;
	screenCtx = emulator.screen.getContext("2d");
	backBuffer =
		screenCtx.createImageData(emulator.screen.width,emulator.screen.height);
	ml.onmousedown = mm.onmousedown = mr.onmousedown = function(e) {
		e.preventDefault();
		ml.className = mm.className = mr.className = "mousebtn";
		this.className = "mousebtn active";
		activeButton = this.dataset.button;
		interClickButton = 0;
	};
	ml.onmouseup = mm.onmouseup = mr.onmouseup = function(e) {
		if (this.dataset.button != activeButton) {
			interClickButton = this.dataset.button;
			this.className="mousebtn interclick";
		}
	}
	emulator.screen.tabIndex = 1000;
	emulator.screen.style.outline = "none";
	emulator.screen.onkeydown = function(e) {
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
	emulator.screen.onkeyup = function(e) {
		if (e.keyCode == 18 && !e.ctrlKey) {
			emulator.registerMouseButton(2, false);
			e.preventDefault();
		}
	};
	emulator.screen.onkeypress = function(e) {
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
	emulator.screen.onmousemove = function(e) {
		let scrollX = document.body.scrollLeft;
		let scrollY = document.body.scrollTop;
		let x = e.clientX - emulator.screen.offsetLeft + scrollX;
		let y = -(e.clientY - emulator.screen.offsetTop + scrollY) + emulator.screen.height - 1;
		emulator.registerMousePosition(x, y);
	};
	emulator.screen.onmousedown = function(e) {
		var button = e.button + 1;
		if (button == 1) button = activeButton;
		emulator.registerMouseButton(button, true);
	};
	emulator.screen.onmouseup = function(e) {
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
	emulator.screen.oncontextmenu = function(e) {
		e.preventDefault();
		return false;
	}
	emulator.screen.focus();
};
