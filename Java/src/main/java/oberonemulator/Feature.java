package oberonemulator;

import java.util.EnumSet;

public enum Feature {

	FLOATING_POINT,
	BW_GRAPHICS, COLOR_GRAPHICS, COLOR16_GRAPHICS, DYNSIZE_GRAPHICS,
	NEW_DYNSIZE_GRAPHICS,
	NATIVE_KEYBOARD, PARAVIRTUAL_KEYBOARD,
	WILDCARD_PCLINK,
	NATIVE_DISK, PARAVIRTUAL_DISK,
	PARAVIRTUAL_CLIPBOARD, HOST_FILESYSTEM,
	SPI, SERIAL, MULTI_SERIAL, SPI_NETWORK, PARAVIRTUAL_WIZNET,
	POWER_MANAGEMENT, LARGE_ADDRESS_SPACE;

	public static final EnumSet<Feature> NONE = EnumSet.noneOf(Feature.class);

	public static final EnumSet<Feature> ALL = EnumSet.of(FLOATING_POINT,
			BW_GRAPHICS, COLOR_GRAPHICS, COLOR16_GRAPHICS,
			DYNSIZE_GRAPHICS, NEW_DYNSIZE_GRAPHICS,
			NATIVE_KEYBOARD, PARAVIRTUAL_KEYBOARD,
			WILDCARD_PCLINK, NATIVE_DISK, PARAVIRTUAL_DISK, HOST_FILESYSTEM,
			PARAVIRTUAL_CLIPBOARD, SPI, SERIAL, MULTI_SERIAL, SPI_NETWORK, PARAVIRTUAL_WIZNET,
			POWER_MANAGEMENT, LARGE_ADDRESS_SPACE);

	public static final EnumSet<Feature> NATIVE = EnumSet.of(FLOATING_POINT,
			BW_GRAPHICS, COLOR16_GRAPHICS, NATIVE_KEYBOARD, NATIVE_DISK, SPI, SERIAL, SPI_NETWORK);

	public static final EnumSet<Feature> JS = EnumSet.of(BW_GRAPHICS,
			DYNSIZE_GRAPHICS, COLOR16_GRAPHICS, PARAVIRTUAL_KEYBOARD, PARAVIRTUAL_DISK,
			PARAVIRTUAL_CLIPBOARD, POWER_MANAGEMENT);

	public static final EnumSet<Feature> C = EnumSet.of(FLOATING_POINT, BW_GRAPHICS,
			NEW_DYNSIZE_GRAPHICS, NATIVE_KEYBOARD, NATIVE_DISK, PARAVIRTUAL_CLIPBOARD,
			SPI, SERIAL, LARGE_ADDRESS_SPACE);

	public static EnumSet<Feature> allowedFeatures = null;

	private boolean isAllowed() {
		return allowedFeatures == null || allowedFeatures.contains(this);
	}

	public void use() {
		if (!isAllowed()) {
			throw new IllegalStateException("Feature "+this.toString()+" is disabled");
		}
	}

	public static EnumSet<Feature> parse(String features) {
		String[] parts = features.toUpperCase().split("(?=[+-])");
		EnumSet<Feature> result;
		switch(parts[0]) {
		case "NONE": result = NONE; break;
		case "ALL": result=ALL; break;
		case "NATIVE": result=NATIVE; break;
		case "JS": result=JS; break;
		case "C": result= C; break;
		default: throw new IllegalArgumentException("Invalid base feature set: "+parts[0]);
		}
		result = EnumSet.copyOf(result);
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].startsWith("+"))
				result.add(Feature.valueOf(parts[i].substring(1)));
			else if (parts[i].startsWith("-"))
				result.remove(Feature.valueOf(parts[i].substring(1)));
			else
				throw new IllegalArgumentException("Invalid feature modifier: "+parts[i]);
		}
		return result;
	}
}
