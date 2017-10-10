var emulator;

window.onload = function() {
	let params = Object.create(null);
	let pairs = window.location.search.substr(1).split("&");
	for (let i = 0, n = pairs.length; i < n; ++i) {
		let [ name, value ] = pairs[i].split("=");
		params[name] = value;
	}
	let { image, width, height } = params;
	emulator = new WebDriver(image, width | 0, height | 0);
}

function WebDriver(imageName, width, height) {
	// Init callback suitable for passing to `setTimeout`.  This is necessary
	// in order for `this` to resolve correctly within the method body.
	this.$run = this.run.bind(this);

	this._initWidgets(width, height);

	this.disk = [];
	this.keyBuffer = [];
	this.startMillis = Date.now();

	this.screenUpdater = new ScreenUpdater(
		this.screen.getContext("2d"), width, height
	);

	this.machine = new RISCMachine();
	this.clipboard = new Clipboard(this.clipboardInput);
	this.virtualKeyboard = new VirtualKeyboard(this.screen, this);
	this.sync = new DiskSync(this);

	// We save no reference because we don't need one; we just want to kick
	// off the load, be notified when it's done, and then let this get GCed.
	new ImageReader(imageName, this);
}

{
	let $proto = WebDriver.prototype;

	$proto.$run = null;

	$proto.buttonBox = null;
	$proto.clickLeft = null;
	$proto.clickMiddle = null;
	$proto.clickRight = null;
	$proto.clipboardInput = null;
	$proto.leds = null;
	$proto.saveLink = null;
	$proto.screen = null;

	$proto.activeButton = 1;
	$proto.clipboard = null;
	$proto.cpuTimeout = null;
	$proto.disk = null;
	$proto.interclickButton = 0;
	$proto.keyBuffer = null;
	$proto.machine = null;
	$proto.mouse = null;
	$proto.paravirtPointer = 0;
	$proto.paused = false;
	$proto.screenUpdater = null;
	$proto.startMillis = null;
	$proto.sync = null;
	$proto.virtualClipboard = null;
	$proto.waitMillis = 0;

	$proto.__defineGetter__("tickCount", function() {
		return Date.now() - this.startMillis;
	});

	$proto.reset = function(cold) {
		this.machine.cpuReset(cold);
		if (cold) {
			let base = this.machine.DisplayStart / 4;
			this.machine.memWriteWord(base, 0x53697A65); // magic value 'Size'
			this.machine.memWriteWord(base + 1, this.screen.width);
			this.machine.memWriteWord(base + 2, this.screen.height);
		}
		this.reschedule();
	};

	$proto.reschedule = function() {
		if (this.cpuTimeout != null) window.clearTimeout(this.cpuTimeout);
		this.cpuTimeout = window.setTimeout(this.$run, 1);
	};

	$proto.run = function() {
		if (this.paused) return;
		let now = Date.now();
		for (var i = 0; i < 200000 && this.waitMillis < now; ++i) {
			this.machine.cpuSingleStep();
		}
		this.cpuTimeout = window.setTimeout(
			this.$run, Math.max(this.waitMillis - Date.now(), 10)
		);
	};

	$proto.wait = function(x) {
		if (this.waitMillis === -1) {
			this.waitMillis = 0;
		}
		else {
			this.waitMillis = this.startMillis + x;
		}
	};

	$proto.registerVideoChange = function(offset, value) {
		let x = (offset % 32) * 32;
		let y = this.screen.height - 1 - (offset / 32 | 0);
		if (y < 0 || x >= this.screen.width) return;
		let base = (y * this.screen.width + x) * 4;
		let { data } = this.screenUpdater.backBuffer;
		for (let i = 0; i < 32; i++) {
			let lit = ((value & (1 << i)) != 0);
			data[base++] = lit ? 0xfd : 0x65;
			data[base++] = lit ? 0xf6 : 0x7b;
			data[base++] = lit ? 0xe3 : 0x83;
			data[base++] = 255;
		}
		this.screenUpdater.mark(x, y);
	};

	$proto.registerMousePosition = function(x, y) {
		let before = this.mouse;

		let after = this.mouse;
		if (0 <= x && x < 4096) after = (after & ~0x00000FFF) | x;
		if (0 <= y && y < 4096) after = (after & ~0x00FFF000) | (y << 12);

		if (before === after) return;

		this.mouse = after;
		this.wait(-1);
		this.reschedule();
	};

	$proto.registerMouseButton = function(button, down) {
		if (1 <= button && button <= 3) {
			let bit = 1 << (27 - button);
			if (down) {
				this.mouse |= bit;
			}
			else {
				this.mouse &= ~bit;
			}
		}
		this.wait(-1);
		this.reschedule();
	};

	$proto.registerLEDs = function(bitstring) {
		for (let i = 0; i < 8; i++) {
			this.leds[i].classList.toggle("lit", (bitstring & (1 << i)));
		}
	};

	$proto.storageRequest = function(value, memory) {
		let address = this.paravirtPointer / 4;
		if ((value & 0xC0000000) === 0) {
			// set pointer
			this.paravirtPointer = value | 0;
			return;
		}
		if ((value & 0xC0000000) === (0x80000000 | 0)) {
			// read
			let sectorNumber = (value - 0x80000000) | 0;
			let sector = this.disk[sectorNumber | 0];
			if (!sector) sector = new Int32Array(256);
			memory.set(sector, address);
			return;
		}
		if ((value & 0xC0000000) === (0xC0000000 | 0)) {
			// write
			let sectorNumber = (value - 0xC0000000) | 0;
			let sector = new Int32Array(256);
			sector.set(memory.subarray(address, address + 256));
			this.disk[sectorNumber | 0] = sector;
			return;
		}
	};

	$proto.registerKey = function(keyCode) {
		this.keyBuffer.push(keyCode << 24);
		this.wait(-1);
		this.reschedule();
	};

	$proto.hasInput = function() {
		return this.keyBuffer.length > 0;
	};

	$proto.getInputStatus = function() {
		if (!this.hasInput()) return this.mouse;
		return this.mouse | 0x10000000;
	};

	$proto.getKeyCode = function() {
		if (!this.hasInput()) return 0;
		return this.keyBuffer.shift();
	};

	$proto.toggleClipboard = function() {
		this.clipboardInput.style.width = this.screen.width;
		if (this.clipboardInput.style.visibility == "hidden") {
			this.clipboardInput.style.visibility = "visible";
			this.clipboardInput.style.height = 200;
		}
		else {
			this.clipboardInput.style.visibility = "hidden";
			this.clipboardInput.style.height = 0;
		}
	};

	$proto.exportDiskImage = function() {
		this.sync.save(this.disk, this.saveLink);
	};

	$proto._initWidgets = function(width, height) {
		let $ = document.getElementById.bind(document);
		this.leds = [
			$("led0"), $("led1"), $("led2"), $("led3"),
			$("led4"), $("led5"), $("led6"), $("led7")
		];

		this.buttonBox = $("buttonbox");
		this.clipboardInput = $("clipboardText");
		this.screen = $("screen");

		this.saveLink = $("exportbutton").parentNode;

		this.screen.width = width;
		this.screen.height = height;

		this.screen.addEventListener("mousemove", this, false);
		this.screen.addEventListener("mousedown", this, false);
		this.screen.addEventListener("mouseup", this, false);
		this.screen.addEventListener("contextmenu", this, false);

		this.screen.focus();

		$ = document.querySelector.bind(document);
		this.clickLeft = $(".mousebtn[data-button='1']");
		this.clickMiddle = $(".mousebtn[data-button='2']");
		this.clickRight = $(".mousebtn[data-button='3']");

		this.buttonBox.addEventListener("mousedown", this, false);
		this.buttonBox.addEventListener("mouseup", this, false);

		this.toggleClipboard();
	};

	// DOM Event handling

	$proto.handleEvent = function(event) {
		switch (event.type) {
			case "load": return void(this._onImageLoad(event));
			case "mousemove": return void(this._onMouseMove(event));
			case "mousedown": return void(this._onMouseButton(event));
			case "mouseup": return void(this._onMouseButton(event));
			case "contextmenu": return void(event.preventDefault());
			default: throw new Error("got event " + event.type);
		}
	};

	$proto._onImageLoad = function(event) {
		this.disk = event.target.reader.contents;
		this.reset(true);
	};

	$proto._onMouseMove = function(event) {
		let { offsetLeft, offsetTop } = this.screen;
		let scrollX = document.body.scrollLeft;
		let scrollY = document.body.scrollTop;
		let x = event.clientX - offsetLeft + scrollX;
		let y = -(event.clientY - offsetTop + scrollY) + this.screen.height - 1;
		this.registerMousePosition(x, y);
	};

	$proto._onMouseButton = function(event) {
		if (event.target !== this.screen) return this._onButtonSelect(event);

		let button = event.button + 1;
		if (event.type === "mousedown") {
			if (button === 1) button = this.activeButton;
			this.registerMouseButton(button, true);
		}
		else {
			if (button === 1) {
				if (this.interclickButton !== 0) {
					this.registerMouseButton(this.interclickButton, true);
					this.registerMouseButton(this.interclickButton, false);
				}
				button = this.activeButton;
			}
			this.registerMouseButton(button, false);
		}
	};

	$proto._onButtonSelect = function(event) {
		let clickButton = event.target;
		if (event.type === "mousedown") {
			event.preventDefault();
			this.clickLeft.className = "mousebtn";
			this.clickMiddle.className = "mousebtn";
			this.clickRight.className = "mousebtn";

			clickButton.classList.add("active");

			this.activeButton = clickButton.dataset.button;
			this.interclickButton = 0;
		}
		else {
			if (clickButton.dataset.button === this.activeButton) return;
			this.interclickButton = clickButton.dataset.button;
			clickButton.classList.add("interclick");
		}
	};
}

function ScreenUpdater(context, width, height) {
	this.context = context;
	this.backBuffer = context.createImageData(width, height);

	this.clear();
}

{
	let $proto = ScreenUpdater.prototype;

	$proto.backBuffer = null;
	$proto.context = null;
	$proto.maxX = null;
	$proto.maxY = null;
	$proto.minX = null;
	$proto.minY = null;
	$proto.update = null;

	ScreenUpdater.paint = $proto.paint = function(updater) {
		updater.context.putImageData(
			updater.backBuffer, 0, 0, updater.minX, updater.minY,
			updater.maxX - updater.minX + 1, updater.maxY - updater.minY + 1
		);
		updater.clear();
	};

	$proto.mark = function(x, y) {
		if (x < this.minX) this.minX = x;
		if (y < this.minY) this.minY = y;
		if (x > this.maxX) this.maxX = x + 31;
		if (y > this.maxY) this.maxY = y;
		if (!this.update) this.update = window.setTimeout(this.paint, 1, this);
	};

	$proto.clear = function() {
		this.minX = this.minY = 4096;
		this.maxX = this.maxY = 0;
		this.update = null;
	};
}

function Clipboard(widget) {
	this._input = widget;
}

{
	let $proto = Clipboard.prototype;

	$proto._buffer = null;
	$proto._input = null;
	$proto._count = null;

	$proto.__defineGetter__("size", function() {
		// assert(this._buffer === null)
		// assert(this._count === null)
		this._buffer = this._input.value.split("\n").join("\r").split("");
		return this._buffer.length;
	});

	$proto.expect = function(count) {
		// assert(this._buffer === null)
		// assert(this._count === null)
		this._buffer = [];
		this._count = count;
	};

	$proto.put = function(charBits) {
		// assert(this._buffer !== null)
		// assert(this._count > 0)
		this._buffer.push(String.fromCharCode(charBits));
		this._count--;
		if (this._count === 0) {
			this._input.value = this._buffer.join("").split("\r").join("\n");
			this._buffer = null;
			this._count = null;
		}
	};

	$proto.get = function() {
		// assert(this._buffer.length > 0)
		let singleChar = this._buffer.shift();
		if (this._buffer.length === 0) this._buffer = null;
		// XXX Warn for non-ASCII?
		return singleChar.charCodeAt(0) | 0;
	};
}

// TODO: Avoid anything exotic for virtualized keys.
//
// For example, Macs don't typically make the F keys easily discoverable.
// They also don't have a real delete key.  Chromebooks neither have a delete
// key nor do they have F11 and F12 keys.  The F keys that do exist are worse
// than undiscoverable--some of theme aren't even usable as F keys unless you
// get rid of ChromeOS.  So in making our choices to virualize things that may
// be difficult or impossible to access (such as middle click, interclicks) we
// need to make sure avoid all of these, otherwise we're offering one
// impossibility as an alternative to another impossibility.
//
// What seems to be relatively safe are Ctrl, Alt, and Shift.  I'm not sure
// that Esc is safe on the MacBook Touchbar.  However, presently we're already
// using Alt to emulate middle click, its limitations have made themselves
// known, given that we're targeting the Web.  However, the emulators also use
// Alt to emulate middle click.  This is prone to error, given that Alt+Left
// and Alt+Right are popular shortcuts for back and forwards, and Firefox
// shows the menubar when tapping Alt.  When switching desktops using a
// keyboard shortcut that involves Alt, releasing the Alt key after landing on
// the desired desktop still fires if that desktop contains an focused window
// running our emulator.  So Alt should be regarded as partially safe:
// keyboard shortcuts involving Alt (including bare Alt), but mouse clicks
// with Alt modifiers should be okay.
//
// We also need to be sensitive to platform conventions.  We aren't right now,
// but we should be doing something like:
//
//   * Alt+Click -> right
//   * Ctrl+Click (non-mac) -> middle
//   * Ctrl+Click (Mac) -> right
//   * Cmd+Click (Mac) -> middle
//   * Cmd+Ctrl+Click (Mac) -> right
//   * Cmd+Alt+Click (Mac) -> right
//
// (The latter two are said to be the typical way that applications simulate
// non-Mac Ctrl+click in Mac apps, but this hasn't been verified.)
function VirtualKeyboard(screen, emulator) {
	// Reading keyboard input on the Web is still a big mess.
	screen.addEventListener("keydown", function(event) {
		let code = event.keyCode;
		// Backspace, Tab, Enter, or Escape
		if (code === 8 || code === 9 || code === 13 || code == 27) {
			event.preventDefault();
			emulator.registerKey(code);
			return;
		}
		// Insert or F1
		if (code === 45 || code === 112) {
			event.preventDefault();
			emulator.registerKey(26);
			return;
		}
		// Alt
		if (code === 18 && !event.ctrlKey) {
			event.preventDefault();
			emulator.registerMouseButton(2, true);
			return;
		}
	});
	screen.addEventListener("keyup", function(event) {
		// Alt
		if (event.keyCode === 18 && !event.ctrlKey) {
			event.preventDefault();
			emulator.registerMouseButton(2, false);
			return;
		}
	});
	screen.addEventListener("keypress", function(event) {
		let code = event.keyCode;
		// Backspace, Tab, Enter, or Escape
		if (code === 8 || code === 9 || code === 13 || code == 27) {
			event.preventDefault();
			emulator.registerKey(code);
			return;
		}
		// Insert or F1
		if (event.charCode === 0 && (code === 45 || code === 112)) {
			event.preventDefault();
			emulator.registerKey(26);
			return;
		}
		// printable char
		if (event.charCode !== 0) {
			event.preventDefault();
			emulator.registerKey(event.charCode | 0);
			return;
		}
	});
}

function ImageReader(imageName, observer) {
	this.container = new Image();
	this.container.reader = this;

	this.contents = null;
	this.handleEvent = function(event) {
		let canvas = document.createElement("canvas");
		let width = canvas.width = this.container.width;
		let height = canvas.height = this.container.height;
		let context = canvas.getContext("2d");

		context.drawImage(this.container, 0, 0);
		let { data } = context.getImageData(0, 0, width, height);
		this.contents = ImageReader.unpack(data, width, height);

		observer.handleEvent(event);
	}

	this.container.addEventListener("load", this);
	this.container.src = imageName + ".png";
}

ImageReader.unpack = function(imageData, width, height) {
	let contents = [];
	for (let i = 0; i < height; i++) {
		let sectorWords = new Int32Array(width / 4);
		for (let j = 0; j < width / 4; j++) {
			let b = i * 4096 + j * 16 + 2;
			sectorWords[j] =
				((imageData[b +  0] & 0xFF) <<  0) |
				((imageData[b +  4] & 0xFF) <<  8) |
				((imageData[b +  8] & 0xFF) << 16) |
				((imageData[b + 12] & 0xFF) << 24) |
				0;
		}
		contents[i] = sectorWords;
	}

	return contents;
};

function DiskSync(observer) {
	this.observer = observer;
}

{
	let $proto = DiskSync.prototype;

	$proto.load = function(file) {
		throw new Error("unimplemented"); // XXX
		return this;
	};

	$proto.save = function(sectors, link) {
		link.href = URL.createObjectURL(new Blob(sectors));
		link.setAttribute("download", "oberon.dsk");
		link.click();
		link.removeAttribute("href");
		link.removeAttribute("download");
	};
}
