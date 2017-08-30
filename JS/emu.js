emuInit = function(width, height) {
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
	emulator.screen.onmousedown = function(e) {
		var button = e.button + 1;
		if (button == 1) button = emulator.activeButton;
		emulator.registerMouseButton(button, true);
	};
	emulator.screen.onmouseup = function(e) {
		var button = e.button + 1;
		if (button == 1) {
			if (emulator.interClickButton != 0) {
				emulator.registerMouseButton(emulator.interClickButton, true);
				emulator.registerMouseButton(emulator.interClickButton, false);
			}
			button = emulator.activeButton;
		}
		emulator.registerMouseButton(button, false);
	};
	emulator.screen.oncontextmenu = function(e) {
		e.preventDefault();
		return false;
	}
	emulator.screen.focus();
};
