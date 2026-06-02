package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;

/**
 * Data Transfer Object (DTO) radice utilizzato per modellare e deserializzare la risposta JSON 
 * nativa restituita dall'endpoint {@code /geocode/search} di OpenRouteService.
 * <p>
 * <b>Contesto Architetturale e Design (Defensive Deserialization):</b><br>
 * Questo record mappa la struttura formale delle specifiche <b>GeoJSON (RFC 7946)</b>. 
 * È configurato per consentire a Jackson un parsing chirurgico ad alte prestazioni: preleva solo 
 * l'albero dei dati geometrici ed esclude proattivamente tutti i metadati e le proprietà testuali 
 * superflue (come i livelli di confidenza dell'indirizzo o i codici postali), minimizzando l'impatto 
 * sulla RAM della JVM.
 * </p>
 * <p>
 * <b>Design Pattern (Immutability):</b><br>
 * L'utilizzo dei Java Records annidati garantisce l'immutabilità assoluta di tutta la gerarchia 
 * dei dati dal momento dello spacchettamento HTTP fino al consumo nel Service Layer (Thread-Safety intrinseca).
 * </p>
 * @param features La collezione (lista) di elementi di tipo {@link Feature} che rappresentano le 
 * corrispondenze geografiche trovate dal motore cartografico, ordinate per indice di rilevanza.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
public record OrsGeocodeResponseDTO(List<Feature> features) {
	/**
	 * Rappresenta una singola entità geografica (Feature) all'interno della risposta GeoJSON.
	 * Funge da contenitore intermedio per isolare i dati spaziali della rotta o della coordinata.
	 * @param geometry L'oggetto geometrico concreto {@link Geometry} associato alla feature corrente.
	 */
	public record Feature(Geometry geometry) {}
	
	/**
	 * Incapsula la geometria spaziale e la matematica dei vettori del punto geolocalizzato.
	 * <p>
	 * <b>Risoluzione dello standard GeoJSON (Coordinate Order):</b><br>
	 * Nello standard internazionale GeoJSON, la geometria di tipo "Point" esprime la posizione 
	 * come una collezione di valori numerici ordinati rigorosamente secondo la convenzione matematica 
	 * degli assi cartesiani: il primo elemento rappresenta l'asse X (Longitudine) e il secondo l'asse Y (Latitudine).
	 * </p>
	 * @param coordinates La lista di valori decimali estratti dal JSON. L'indice 0 ospita sempre la 
	 * longitudine, l'indice 1 ospita la latitudine.
	 */
	public record Geometry(List<Double> coordinates) {
		/**
		 * Estrae in modo mirato la Longitudine (Asse X) dall'array di coordinate.
		 * <p>
		 * <b>Nota di Sicurezza Informatica (Robustness):</b><br>
		 * Questo metodo normalizza l'accesso al dato GeoJSON nativo. Mantenendo il puntamento fisso 
		 * sull'indice 0, evita inversioni accidentali degli assi nel codice client, un'anomalia catastrofica 
		 * nel dominio logistico che porterebbe il sistema a confondere i poli geografici.
		 * </p>
		 * @return Il valore {@code Double} che esprime la longitudine in gradi decimali (sistema di riferimento WGS 84).
		 * @throws IndexOutOfBoundsException Se l'array restituito dal provider esterno è corrotto o contiene meno di un elemento.
		 */
		public Double getLongitude() { 
			return coordinates.get(0); 
		}
		/**
		 * Estrae in modo mirato la Latitudine (Asse Y) dall'array di coordinate.
		 * <p>
		 * <b>Nota di Sicurezza Informatica (Robustness):</b><br>
		 * Mantiene il puntamento fisso e documentato sull'indice 1 per isolare il componente di 
		 * latitudine, intercettando a monte la discordanza di formato rispetto alla convenzione 
		 * applicativa umana standard (che solitamente pronuncia e scrive prima la Latitudine e poi la Longitudine).
		 * </p>
		 * @return Il valore {@code Double} che esprime la latitudine in gradi decimali (sistema di riferimento WGS 84).
		 * @throws IndexOutOfBoundsException Se l'array restituito dal provider esterno è corrotto o contiene meno di due elementi.
		 */
		public Double getLatitude() { 
			return coordinates.get(1); 
		}
	}
}
