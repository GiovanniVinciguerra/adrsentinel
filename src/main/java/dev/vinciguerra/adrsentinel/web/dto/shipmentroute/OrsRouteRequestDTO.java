package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object (DTO) che modella il payload JSON di richiesta (POST) da inviare 
 * all'endpoint di routing spaziale di OpenRouteService (profilo {@code driving-hgv}).
 * <p>
 * <b>Ottimizzazione del Payload (Stealth Mode JSON):</b><br>
 * L'annotazione {@link JsonInclude}({@code JsonInclude.Include.NON_NULL}) a livello di classe è una 
 * direttiva architetturale critica. Istruisce il serializzatore Jackson a omettere completamente 
 * dall'albero JSON qualsiasi parametro o ramo avente valore {@code null}. 
 * Questo garantisce che se un veicolo viaggia vuoto (senza restrizioni ADR) o non presenta 
 * limiti fisici (es. altezza non specificata), l'API di ORS non riceverà campi nulli (che 
 * causerebbero eccezioni di validazione lato server HTTP 400), ma applicherà dinamicamente 
 * i parametri standard di fallback per i mezzi pesanti.
 * </p>
 * @param coordinates La matrice geospaziale che definisce i punti di passaggio della rotta (Origine e Destinazione). 
 * Strutturata tassativamente secondo lo standard GeoJSON come Array di Array: {@code [[Longitudine, Latitudine], [Longitudine, Latitudine]]}.
 * @param options L'oggetto annidato che incapsula le personalizzazioni del profilo di routing 
 * (es. dimensioni fisiche del mezzo e normative ADR). Se omesso (null), ORS calcolerà una rotta HGV generica.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrsRouteRequestDTO(List<List<Double>> coordinates, Options options) {
	/**
	 * Wrapper di primo livello per i parametri opzionali di routing.
	 * Isola semanticamente le coordinate geometriche dalle variabili fisiche del veicolo.
	 * @param profileParams I parametri specifici per il profilo "Heavy Goods Vehicle" (HGV).
	 */
	public record Options(@JsonProperty("profile_params") ProfileParams profileParams) {}
	
	/**
	 * Modella la "Profilazione Fisica" del veicolo pesante, traducendo l'entità di database 
	 * in metriche ingegneristiche comprensibili dall'algoritmo di calcolo stradale.
	 * <p>
	 * <b>Nota di Dominio (Conversione Unità di Misura):</b><br>
	 * A differenza del database interno che solitamente memorizza il peso in chilogrammi (Kg), 
	 * l'API di ORS richiede esplicitamente che il valore sia convertito in tonnellate metriche.
	 * </p>
	 * @param restrictions L'oggetto contenente le limitazioni normative e legali del carico in viaggio (es. merci pericolose).
	 */
	public record ProfileParams(Restrictions restrictions) {}
	
	/**
	 * Incapsula le direttive normative restrittive imposte sul carico, comunicando al motore 
	 * cartografico i vincoli tassativi di circolazione (Hard Constraints).
	 * @param weight Il peso effettivo o massimo a pieno carico del veicolo espresso in <b>Tonnellate</b>. 
	 * Utilizzato per evitare ponti con limiti di portata.
	 * @param height L'altezza massima del veicolo al garrese, espressa in <b>Metri</b>. 
	 * Fondamentale per escludere sottopassi, ponti bassi e gallerie non a norma.
	 * @param length La lunghezza totale dell'autoarticolato/autocarro, espressa in <b>Metri</b>. 
	 * Serve a prevenire l'instradamento in tornanti troppo stretti o centri urbani impercorribili.
	 * @param width La larghezza del veicolo, espressa in <b>Metri</b>. Evita l'instradamento in strettoie.
	 * @param axleload Il peso per asse consentito dal veicolo calcolato in modo automatico in base al numero di assi e il peso massimo 
	 * consentito.
	 * @param hazmat Il parametro ufficiale di ORS per le merci ADR
	 */
	public record Restrictions(Double weight, Float height, Float length, Float width, Double axleload, Boolean hazmat) {}
}
