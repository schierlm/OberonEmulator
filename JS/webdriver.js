var emulator;

window.onload = function() {
	emulator = new WebDriver();
}

function WebDriver() {
	this.keyBuffer = [];
	this.waitMillis = 0;

	this.startMillis = Date.now();
	emuInit();
}

{
	let $proto = WebDriver.prototype;

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
		this.cpuTimeout = window.setTimeout(cpuRun, 1);
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
