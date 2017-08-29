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

	this.startMillis = Date.now();
	emuInit();
}

{
	let $proto = WebDriver.prototype;

	$proto.run = null;

	$proto.cpuTimeout = null;
	$proto.keyBuffer = null;
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
		if (!cpuRunning()) return;
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
}
