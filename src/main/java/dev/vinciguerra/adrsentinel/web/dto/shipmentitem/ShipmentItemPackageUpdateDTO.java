package dev.vinciguerra.adrsentinel.web.dto.shipmentitem;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorOnuPackingCode;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageCount;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageType;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageWeight;

/**
 * Data Transfer Object (DTO) immutabile dedicato all'aggiornamento parziale (Patch/Update) 
 * dei dettagli fisici e di imballaggio di una riga di spedizione (Shipment Item).
 * <p><b>Contesto Architetturale (Anti-Corruption Layer &amp; Immutabilità):</b></p>
 * Implementato nativamente come Java {@code record}, questo oggetto garantisce la totale 
 * immutabilità del payload una volta deserializzato dal framework (es. Jackson). 
 * Funge da scudo protettivo primario al confine dell'applicazione (strato REST/Controller): 
 * grazie all'integrazione della <i>Declarative Validation</i> custom, intercetta e respinge 
 * (tramite HTTP 400) qualsiasi input malformato, incompleto o fisicamente incoerente 
 * prima che questo possa inquinare il Domain Model (l'entità {@code ShipmentItem} e il 
 * suo embeddable {@code PackageDetail}).
 * <p><b>Pipeline di Sicurezza (Fail-Fast):</b></p>
 * Ogni singola proprietà è presidiata da un validatore di dominio specifico che assicura:
 * <ul>
 * <li>Sicurezza contro le eccezioni di deserializzazione (es. parsing sicuro degli Enum).</li>
 * <li>Integrità matematica e protezione da buffer overflow o anomalie floating-point (NaN).</li>
 * <li>Conformità sintattica rigida alle grammatiche dell'Accordo ADR (es. codici ONU).</li>
 * </ul>
 * @param packageCount La quantità fisica totale dei colli. Presidiata da {@link ValidatorPackageCount} 
 * per garantire un range realistico e prevenire errori di battitura macroscopici.
 * @param packageType La tipologia di imballaggio (es. DRUM, IBC, TANK). Trasmessa intenzionalmente 
 * come {@code String} e presidiata da {@link ValidatorPackageType} per implementare 
 * il pattern <i>Fail-Safe Enum Parsing</i>, evitando il crash diretto dell'API in caso 
 * di valori non a dizionario.
 * @param onuPackingCode Il codice o la lista di codici di omologazione (es. 4G, 31HA1). Presidiato da 
 * {@link ValidatorOnuPackingCode} per validare la grammatica ADR e blindare la 
 * lunghezza della stringa a protezione del database.
 * @param packagingWeightkg Il peso della tara espresso in chilogrammi. Presidiato da {@link ValidatorPackageWeight} 
 * per garantire la conformità ai limiti strutturali dei contenitori (max 500 kg) 
 * e prevenire vulnerabilità algoritmiche (isInfinite/isNaN).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentItemPackageUpdateDTO(@ValidatorPackageCount Integer packageCount, @ValidatorPackageType String packageType,
	@ValidatorOnuPackingCode String onuPackingCode, @ValidatorPackageWeight Float packagingWeightkg) {}
