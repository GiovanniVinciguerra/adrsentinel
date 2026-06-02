package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

/**
 * Data Transfer Object (DTO) immutabile deputato al trasporto e all'esposizione 
 * di singole coordinate geografiche verso il client (Presentation Layer).
 * <p>
 * <b>Contesto Architetturale e Normalizzazione:</b><br>
 * Questo record svolge un ruolo cruciale come "Normalizzatore Cartografico". 
 * Molti sistemi GIS e API esterne (come lo standard GeoJSON di OpenRouteService) 
 * gestiscono le coordinate nell'ordine matematico {@code [Longitudine (X), Latitudine (Y)]}. 
 * Questo DTO inverte e stabilizza l'ordine nel formato standard umanamente leggibile 
 * e accettato dalla stragrande maggioranza delle librerie frontend (es. Leaflet.js, Google Maps API), 
 * ovvero {@code {latitude, longitude}}, prevenendo alla radice il gravissimo "Coordinate Swap Bug".
 * </p>
 * <p>
 * <b>Design Pattern:</b><br>
 * Sfruttando la natura nativa dei {@code record} di Java, l'oggetto garantisce 
 * un'immutabilità assoluta (Thread-Safety), rendendolo perfetto e ad altissime 
 * prestazioni durante la fase di serializzazione in formato JSON tramite Jackson.
 * </p>
 * @param latitude La latitudine del punto spaziale (Asse Y). 
 * Espressa in gradi decimali (Decimal Degrees) secondo il sistema di riferimento 
 * geodetico standard <b>WGS 84</b>. Rappresenta la distanza angolare dall'equatore 
 * (valori tipici compresi tra -90.0 e +90.0).
 * @param longitude La longitudine del punto spaziale (Asse X). 
 * Espressa in gradi decimali (Decimal Degrees) secondo il sistema di riferimento 
 * geodetico standard <b>WGS 84</b>. Rappresenta la distanza angolare dal meridiano 
 * di Greenwich (valori tipici compresi tra -180.0 e +180.0).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record GeoCoordinateResponseDTO(Double latitude, Double longitude) {}
