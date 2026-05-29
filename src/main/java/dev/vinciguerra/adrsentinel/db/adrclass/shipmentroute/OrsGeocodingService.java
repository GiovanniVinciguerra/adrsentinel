package dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import dev.vinciguerra.adrsentinel.exception.AddressNotResolvableException;
import dev.vinciguerra.adrsentinel.exception.GeocodingApiException;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.GeoCoordinateResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsGeocodeResponseDTO;

@Service
public class OrsGeocodingService {
	private static final Logger logger = LoggerFactory.getLogger(OrsGeocodingService.class);
	
	private final RestClient restClient;
	private final String apiKey;
	
	public OrsGeocodingService(
			RestClient.Builder restBuilder, 
			@Value("${ors.api.base-url:https://api.openrouteservice.org}") String baseUrl, 
			@Value("${ors.api.key}") String apiKey) {
		this.apiKey = apiKey;
		this.restClient = restBuilder.baseUrl(baseUrl).build();
	}
	
	public GeoCoordinateResponseDTO geocodeAddress(String address) {
		logger.info("Starting geocoding for address: [{}]", address);
		try {
			OrsGeocodeResponseDTO response = restClient.get()
				.uri(
					uriBuilder -> uriBuilder
						.path("/geocode/search")
						.queryParam("api_key", apiKey)
						.queryParam("text", address)
						.queryParam("size", 1).build()
					)
				.retrieve()
				.body(OrsGeocodeResponseDTO.class);
			return Optional.ofNullable(response)
				.filter(res -> res.features() != null && !res.features().isEmpty())
				.map(res -> res.features().get(0))
				.filter(feat -> feat.geometry() != null && feat.geometry().coordinates() != null)
				.filter(feat -> feat.geometry().coordinates().size() >= 2)
				.map(feat -> new GeoCoordinateResponseDTO(feat.geometry().getLatitude(), feat.geometry().getLongitude()))
				.orElseThrow(() -> new AddressNotResolvableException(address));
		} catch(RestClientException error) {
			logger.info("Infrastructure failure while geocoding address [{}]. Cause: {}", address, error.getMessage());
			throw new GeocodingApiException(error.getMessage(), error);
		}
	}
}
