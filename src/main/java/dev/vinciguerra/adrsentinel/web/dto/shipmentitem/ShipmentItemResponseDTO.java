package dev.vinciguerra.adrsentinel.web.dto.shipmentitem;

import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem.PackageDetail;

/**
 * Data Transfer Object (DTO) in uscita (Response Payload) che incapsula e trasporta 
 * in modo sicuro le informazioni di una singola riga di carico (Shipment Item) verso i client API.
 * <p><b>Contesto Architetturale (Immutabilità e Information Hiding):</b></p>
 * Progettato sfruttando il costrutto nativo {@code record} di Java, questo oggetto garantisce 
 * <i>Immutabilità Intrinseca</i> e <i>Thread-Safety</i> assoluta. Agisce come barriera 
 * architetturale (Information Hiding) tra il layer di persistenza (Entità Hibernate gestite) 
 * e il livello di presentazione (JSON/REST). Impedisce la serializzazione accidentale di 
 * metadati relazionali o proxy dormienti, fornendo al client un contratto dati prevedibile e stabile.
 * <p><b>Contesto di Dominio (Logistica ADR):</b></p>
 * Rappresenta l'entità fisica materiale caricata a bordo del veicolo (es. una cisterna da 1000 Litri 
 * di Gasolio o 50 Fusti di Vernice). Aggrega in un'unica vista strutturata sia le grandezze 
 * fisiche operative (quantità e unità di misura), sia le direttive normative risolte 
 * tramite il riferimento all'anagrafica ONU.
 * <p><b>Composizione del Grafo (Nested DTOs):</b></p>
 * Questo DTO si comporta come un aggregatore di dominio. Trasporta al suo interno le rappresentazioni 
 * materializzate (Nested DTOs) della spedizione padre ({@link ShipmentResponseDTO}) e della normativa 
 * ADR applicabile ({@link OnuNumberResponseDTO}), fornendo al frontend una "fotografia" completa 
 * e gerarchica, immediatamente renderizzabile nella User Interface senza richiedere ulteriori fetch.
 * @param uuid La Business Key pubblica e inalterabile (UUID) dell'articolo, utilizzata dai client 
 * per operazioni REST sicure (es. interrogazioni puntuali, update, delete) prevenendo attacchi IDOR.
 * @param quantity La grandezza fisica e quantitativa della materia caricata (es. 100).
 * @param netWeightkg La quantità espressa in kilogrammi della materia caricata (es. 100)
 * @param unitOfMeasure La stringa rappresentante l'unità di misura (es. "KG", "L"), derivata 
 * dalla decodifica sicura (Type Flattening) dell'Enum interno.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record ShipmentItemResponseDTO(String uuid, Integer quantity, Integer netWeightkg, String unitOfMeasure, ShipmentItemPackageDetailResponseDTO packageDetail) {
	/**
	 * Data Transfer Object (DTO) immutabile deputato alla serializzazione in uscita (Response Payload) 
	 * dei dettagli fisici e di imballaggio di una riga di spedizione.
	 * <p><b>Contesto Architetturale (Data Hiding &amp; Presentation Layer):</b></p>
	 * Implementato nativamente come Java {@code record}, questo oggetto funge da strato di isolamento 
	 * tra il Domain Model (le entità JPA gestite da Hibernate) e il client REST esterno. 
	 * Il suo scopo primario è l'incapsulamento (Data Hiding): omette deliberatamente le chiavi 
	 * primarie interne del database (es. ID sequenziali) e le dipendenze bidirezionali, esponendo 
	 * al client esclusivamente dati sicuri, appiattiti (flat) e rilevanti per la business logic.
	 * @param uuid L'identificativo pubblico (Business Key) generato dal sistema per questo specifico dettaglio 
	 * di imballaggio, utilizzato dal client per successive operazioni di tracciamento o aggiornamento (Patch/Put).
	 * @param packageCount Il numero totale dei colli fisici (es. 10).
	 * @param packageType La tipologia di imballaggio convertita in formato testuale sicuro (es. "DRUM", "IBC").
	 * @param onuPackingCode La lista dei codici di omologazione ONU associati all'imballaggio (es. "31HA1,4G").
	 * @param packageWeightkg Il peso della tara dell'imballaggio espresso in chilogrammi (kg).
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Output Payload)
	 * @since 1.0
	 */
	public record ShipmentItemPackageDetailResponseDTO(String uuid, Integer packageCount, String packageType, String onuPackingCode,
			Float packageWeightkg) {
		
		/**
		 * Costruttore statico (Static Factory Method) per la mappatura sicura dall'entità di dominio al DTO.
		 * <p><b>Pipeline di Mappatura e Sicurezza:</b></p>
		 * <ul>
		 * <li><i>Null-Safety:</i> Gestisce nativamente i casi in cui i dettagli di imballaggio non siano 
		 * ancora stati compilati (es. merci alla rinfusa o cisterne senza imballaggio), restituendo {@code null} 
		 * in modo sicuro senza scatenare eccezioni di runtime ({@code NullPointerException}).</li>
		 * <li><i>Enum Serialization:</i> Converte esplicitamente l'enumerazione {@code PackageType} del dominio 
		 * nella sua rappresentazione testuale ({@code .name()}), garantendo una deserializzazione JSON nativa 
		 * perfetta da parte di framework come Jackson.</li>
		 * </ul>
		 * @param entity L'oggetto di dominio {@code PackageDetail} (tipicamente un {@code @Embeddable}) 
		 * estratto dal database tramite il Service Layer.
		 * @return Una nuova istanza immutabile del DTO popolata con i dati dell'entità, 
		 * oppure {@code null} se l'entità sorgente è assente.
		 */
		public static ShipmentItemPackageDetailResponseDTO fromEntity(PackageDetail entity) {
			if(entity == null)
				return null;
			
			return new ShipmentItemPackageDetailResponseDTO(
				entity.getDetailUUID(),
				entity.getPackageCount(),
				entity.getPackageType().name(),
				entity.getOnuPackingCode(),
				entity.getPackagingWeightkg()
			);
		}
	}
	
	/**
	 * Factory Method statico per la conversione (Mapping) e l'aggregazione di un'entità 
	 * di dominio {@link ShipmentItem} nel suo corrispondente Data Transfer Object in uscita 
	 * {@link ShipmentItemResponseDTO}.
	 * <p><b>Contesto Architetturale (Dettaglio del Carico ADR):</b></p>
	 * Nel dominio logistico, questo oggetto rappresenta la singola riga di carico (es. un fusto, 
	 * un IBC o una cisterna). Questo metodo si occupa di tradurre la riga dal modello relazionale 
	 * (Hibernate) al contratto API JSON, isolando la logica di estrazione dei dati e garantendo 
	 * che l'infrastruttura del database rimanga invisibile al livello di presentazione.
	 * <p><b>Design Pattern e Strategie di Mapping:</b></p>
	 * <ul>
	 * <li><b>Sicurezza e Robustezza (Guard Clause):</b> Il controllo difensivo iniziale 
	 * ({@code if(entity == null)}) rende il metodo intrinsecamente <i>Null-Safe</i>. Previene 
	 * in modo assoluto le {@code NullPointerException} durante l'elaborazione massiva di 
	 * collezioni (es. generazione della distinta di carico via Stream API).</li>
	 * <li><b>Type Flattening (Serializzazione Enum):</b> L'unità di misura ({@code UnitOfMeasure}) 
	 * viene convertita forzatamente in formato testuale tramite {@code .name()}. Questo disaccoppia 
	 * il payload JSON dalle classi interne di Java, prevenendo errori di serializzazione.</li>
	 * </ul>
	 * @param entity L'istanza dell'entità JPA recuperata dal database, rappresentante una 
	 * specifica riga di carico o collo della spedizione. Ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link ShipmentItemResponseDTO} pronta 
	 * per l'invio HTTP, oppure {@code null} se l'input fornito era assente.
	 */
	public static ShipmentItemResponseDTO fromEntity(ShipmentItem entity) {
		if(entity == null)
			return null;
		
		return new ShipmentItemResponseDTO(
			entity.getItemUUID(),
			entity.getQuantity(),
			entity.getNetWeightkg(),
			entity.getUnitOfMeasure().name(),
			ShipmentItemPackageDetailResponseDTO.fromEntity(entity.getPackageDetails())
		);
	}
}
