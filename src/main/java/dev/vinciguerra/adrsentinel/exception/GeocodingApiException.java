package dev.vinciguerra.adrsentinel.exception;

public class GeocodingApiException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public GeocodingApiException(String message, Throwable cause) {
		super("Communication error with the Geocoding API: " + message, cause);
	}
}
