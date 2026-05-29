package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;

public record OrsGeocodeResponseDTO(List<Feature> features) {
	public record Feature(Geometry geometry) {}
	
	public record Geometry(List<Double> coordinates) {
		public Double getLongitude() { 
			return coordinates.get(0); 
		}
		
		public Double getLatitude() { 
			return coordinates.get(1); 
		}
	}
}
