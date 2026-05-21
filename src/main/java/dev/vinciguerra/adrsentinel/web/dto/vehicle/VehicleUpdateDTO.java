package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorHeight;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLength;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLoadType;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorMaxUsefulWeight;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorMaxWeight;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorVehicleType;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorWidth;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) dedicato alla mutazione 
 * (Aggiornamento Massivo) delle grandezze fisiche e delle classificazioni operative 
 * di un veicolo già censito a sistema.
 * <p><b>Contesto Architetturale (Separation of Payloads e Sicurezza):</b></p>
 * La creazione di un DTO specifico per l'operazione di Update (distinto da quello di Create) 
 * è una best practice fondamentale. Questo record esclude deliberatamente la chiave di business 
 * (Targa) e i parametri legali isolati (come {@code adrCertified}), impedendo fisicamente al client 
 * di tentare la sovrascrittura di dati immutabili o gestiti da flussi separati (prevenzione 
 * della vulnerabilità di <i>Mass Assignment / Overposting</i>).
 * <p><b>Validazione e Integrità del Contratto (Edge Validation):</b></p>
 * Ogni singolo campo è blindato dalle macro-annotazioni di dominio ({@code @ValidatorRequiredString}, 
 * {@code @ValidatorRequiredNumber}). Questo garantisce che il payload non contenga valori nulli, 
 * stringhe vuote o grandezze fisiche negative, sollevando il layer di Business Logic (Service) 
 * dall'onere di validare l'input.
 * <p><b>Design Auto-Documentante (Unit Suffixes):</b></p>
 * Analogamente al payload di creazione, le variabili includono le unità di misura come suffisso 
 * ({@code kg}, {@code m}). Questo contratto API strongly-typed e self-describing annulla il rischio 
 * di errori di conversione algoritmica da parte dei client consumatori.
 * @param vehicleType La nuova classificazione tecnica ADR da assegnare al mezzo (es. "FL", "AT").
 * @param loadType La nuova tipologia di allestimento per il carico (es. "TANK", "BULK").
 * @param maxWeightkg La massa massima ammissibile aggiornata (in chilogrammi).
 * @param maxUsefulWeightkg La nuova portata utile effettiva (in chilogrammi), parametro chiave 
 * che determinerà il riposizionamento del veicolo all'interno delle cache raggruppate.
 * @param heightm L'altezza massima aggiornata del veicolo (in metri).
 * @param widthm La larghezza massima aggiornata del veicolo (in metri).
 * @param lengthm La lunghezza totale aggiornata del veicolo (in metri).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record VehicleUpdateDTO(@ValidatorVehicleType String vehicleType, @ValidatorLoadType String loadType,
	@ValidatorMaxWeight Integer maxWeightkg, @ValidatorMaxUsefulWeight Integer maxUsefulWeightkg,
	@ValidatorHeight Float heightm, @ValidatorWidth Float widthm, @ValidatorLength Float lengthm) {}
