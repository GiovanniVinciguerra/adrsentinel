package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLongitude;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del motore di validazione per il vincolo geografico {@link ValidatorLongitude}.
 * <p>
 * Questa classe viene istanziata e gestita automaticamente dal framework di validazione 
 * (es. Hibernate Validator) per analizzare i campi annotati con {@code @ValidatorLongitude}. 
 * Svolge il ruolo primario di barriera difensiva (Defensive Programming) per garantire che 
 * il sistema cartografico e l'algoritmo di routing (es. OpenRouteService) non tentino di elaborare 
 * coordinate corrotte, assenti o spazialmente impossibili.
 * </p>
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Strict Nullability:</b> Discostandosi dalla convezione standard JSR-380, questo validatore implementa 
 * una politica rigorosa respingendo attivamente i valori {@code null}, centralizzando così 
 * in un unico punto sia l'obbligatorietà del dato che la sua coerenza spaziale.</li>
 * <li><b>Type-Safety (IEEE 754):</b> Previene il crash dei servizi a valle intercettando anomalie 
 * numeriche in virgola mobile generate da errori computazionali (Infinity, Not-a-Number).</li>
 * <li><b>Domain-Safety:</b> Garantisce il rispetto dell'estensione geografica orizzontale del globo terrestre 
 * (l'angolo misurato in direzione Est-Ovest a partire dal Meridiano di Greenwich).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLongitude
 */
public class LongitudeValidator implements ConstraintValidator<ValidatorLongitude, Double> {
	/**
	 * Valuta se il valore della longitudine fornito rispetta i rigidi vincoli fisici e matematici del dominio.
	 * <p>
	 * <b>Flusso di Validazione (Execution Flow):</b>
	 * <ol>
	 * <li><b>Tolleranza Zero (Strict Mode):</b> Se il valore è {@code null}, il metodo restituisce immediatamente {@code false}. 
	 * Questa scelta accorpa logicamente il controllo di presenza (Presence Check) a quello di validità del dominio.</li>
	 * <li><b>Integrità IEEE 754:</b> Verifica che il valore a virgola mobile non sia inficiato da divisioni 
	 * per zero ({@code isInfinite()}) o da operazioni matematiche non risolvibili ({@code isNaN()}).</li>
	 * <li><b>Integrità Geografica:</b> Verifica che la coordinata sia rigorosamente contenuta nell'intervallo 
	 * chiuso [-180.0, 180.0] gradi.</li>
	 * </ol>
	 * </p>
	 * @param value L'istanza numerica di {@link Double} rappresentante la longitudine da validare (può essere null).
	 * @param context L'oggetto contesto fornito dal framework di validazione, utile per alterare dinamicamente 
	 * il grafo degli errori o sovrascrivere il template del messaggio (non impiegato in questo livello base).
	 * @return {@code false} se il valore è assente (null), matematicamente corrotto o geograficamente fuori dai limiti planetari; 
	 * {@code true} se la coordinata è perfetta e pronta per il geocoding strutturale.
	 */
	@Override
	public boolean isValid(Double value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= -180.0 && value <= 180.0;
	}
}
