var emulator;

window.onload = function() {
	emulator = new WebDriver();
}

function WebDriver() {
	// Init callback suitable for passing to `setTimeout`.  This is necessary
	// in order for `this` to resolve correctly within the method body.
	this.$run = this.run.bind(this);

	this.keyBuffer = [];
	this.waitMillis = 0;
	this.paused = false;

	this.startMillis = Date.now();
	emuInit();
}

{
	let $proto = WebDriver.prototype;

	$proto.run = null;

	$proto.cpuTimeout = null;
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
}
