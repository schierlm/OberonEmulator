var emulator;

window.onload = function() {
	let [ imageName, width, height ] =
		window.location.hash.substring(1).split(",");
	emulator = new WebDriver(imageName, width, height);
}

function WebDriver(imageName, width, height) {
	// Init callback suitable for passing to `setTimeout`.  This is necessary
	// in order for `this` to resolve correctly within the method body.
	this.$run = this.run.bind(this);

	this._cacheWidgets();

	this.disk = [];
	this.keyBuffer = [];
	this.waitMillis = 0;
	this.paused = false;

	this.startMillis = Date.now();
	emulator = this; // XXX Remove this when `emuInit` gets refactored out
	emuInit(width, height);

	this.diskLoader = new DiskLoader(imageName, this);
}

{
	let $proto = WebDriver.prototype;

	$proto.run = null;

	$proto.clipboard = null;
	$proto.screen = null;

	$proto.cpuTimeout = null;
	$proto.disk = null;
	$proto.diskLoader = null;
	$proto.keyBuffer = null;
	$proto.paused = null;
	$proto.startMillis = null;
	$proto.waitMillis = null;

	$proto.__defineGetter__("tickCount", function() {
		return Date.now() - this.startMillis;
	});

	$proto.reset = function(cold) {
		cpuReset(cold);
		this.resume();
	};

	$proto.resume = function() {
		if (this.cpuTimeout != null) window.clearTimeout(this.cpuTimeout);
		this.cpuTimeout = window.setTimeout(this.$run, 1);
	};

	$proto.run = function()
	{
		if (this.paused) return;
		let now = Date.now();
		for (var i = 0; i < 200000 && this.waitMillis < now; ++i) {
			cpuSingleStep();
		}
		this.cpuTimeout = window.setTimeout(
			this.$run, Math.max(this.waitMillis - Date.now()), 10
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

	$proto.registerMousePosition = function(x, y) {
		let before = this.mouse;

		let after = this.mouse;
		if (0 <= x && x < 4096) after = (after & ~0x00000FFF) | x;
		if (0 <= y && y < 4096) after = (after & ~0x00FFF000) | (y << 12);

		if (before === after) return;

		this.mouse = after;
		this.wait(-1);
		this.resume();
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
		this.resume();
	};

	$proto.registerKey = function(keyCode) {
		this.keyBuffer.push(keyCode << 24);
		this.wait(-1);
		this.resume();
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

	$proto._cacheWidgets = function() {
	let $ = document.getElementById.bind(document);
	this.clipboard = $("clipboardText");
	this.screen = $("screen");
	};

	// DOM Event handling

	$proto.handleEvent = function(event) {
		switch (event.type) {
			case
				"load": this._onLoad(event);
			break;
			default:
				throw new Error("got event " + event.type);
			break;
		}
	};

	$proto._onLoad = function(event) {
		this.disk = this.diskLoader.contents;
		this.reset(true);
	};
}

function DiskLoader(imageName, observer) {
	this.contents = [];
	this.container = new Image();

	this.handleEvent = function(event) {
		let canvas = document.createElement("canvas");
		let width = canvas.width = this.container.width;
		let height = canvas.height = this.container.height;
		let context = canvas.getContext("2d");

		context.drawImage(this.container, 0, 0);
		let { data } = context.getImageData(0, 0, width, height);
		this.read(data, width, height, this.contents);

		observer.handleEvent(event);
	}

	this.container.addEventListener("load", this);
	this.container.src = imageName + ".png";
}

DiskLoader.prototype.read = function(imageData, width, height, contents) {
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
};
