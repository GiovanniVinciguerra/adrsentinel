package dev.vinciguerra.adrsentinel.exception;

public class AddressNotResolvableException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public AddressNotResolvableException(String address) {
		super("The address provided could not be geolocated: '" + address + "'.");
	}
}
