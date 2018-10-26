/*
Copyright (C) 2008 John Martin

This source code is release under the BSD License.

This file is part of JQuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://jquantlib.org/

JQuantLib is free software: you can redistribute it and/or modify it
under the terms of the JQuantLib license.  You should have received a
copy of the license along with this program; if not, please email
<jquant-devel@lists.sourceforge.net>. The license is also available online at
<http://www.jquantlib.org/index.php/LICENSE.TXT>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the license for more details.

JQuantLib is based on QuantLib. http://quantlib.org/
When applicable, the original copyright notice follows this notice.
 */

 /*
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.JDate;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * This class provides a more comfortable way to instantiate standard market
 * swap.
 *
 * @author John Martin
 */
public class MakeVanillaSwap {

    private final Period swapTenor;
    private final IborIndex iborIndex;
    private final /*@Rate*/ double fixedRate;
    private final Period forwardStart;

    private JDate effectiveDate;
    private Calendar fixedCalendar;
    private Calendar floatCalendar;

    private VanillaSwap.Type type;
    private /*@Real*/ double nominal;
    private Period fixedTenor;
    private Period floatTenor;
    private BusinessDayConvention fixedConvention;
    private BusinessDayConvention fixedTerminationDateConvention;
    private BusinessDayConvention floatConvention;
    private BusinessDayConvention floatTerminationDateConvention;
    private DateGeneration.Rule fixedRule;
    private DateGeneration.Rule floatRule;
    private boolean fixedEndOfMonth;
    private boolean floatEndOfMonth;
    private JDate fixedFirstDate;
    private JDate fixedNextToLastDate;
    private JDate floatFirstDate;
    private JDate floatNextToLastDate;
    private /*@Spread*/ double floatSpread;
    private DayCounter fixedDayCount;
    private DayCounter floatDayCount;
    private JDate terminationDate;
    private PricingEngine engine;

    public MakeVanillaSwap(
            final Period swapTenor,
            final IborIndex index) {
        this(swapTenor, index, 0.0, new Period(0, TimeUnit.Days));
    }

    public MakeVanillaSwap(
            final Period swapTenor,
            final IborIndex index,
            final /*Rate*/ double fixedRate) {
        this(swapTenor, index, fixedRate, new Period(0, TimeUnit.Days));
    }

    public MakeVanillaSwap(
            final Period swapTenor,
            final IborIndex index,
            final /*@Rate*/ double fixedRate,
            final Period forwardStart) {
        this.swapTenor = swapTenor;
        this.iborIndex = index;
        this.fixedRate = fixedRate;
        this.forwardStart = forwardStart;
        this.effectiveDate = new JDate();
        this.fixedCalendar = index.fixingCalendar();
        this.floatCalendar = index.fixingCalendar();
        this.type = VanillaSwap.Type.Payer;
        this.nominal = 1.0;
        this.fixedTenor = new Period(1, TimeUnit.Years);
        this.floatTenor = index.tenor();
        this.fixedConvention = BusinessDayConvention.ModifiedFollowing;
        this.fixedTerminationDateConvention = BusinessDayConvention.ModifiedFollowing;
        this.floatConvention = index.businessDayConvention();
        this.floatTerminationDateConvention = index.businessDayConvention();
        this.fixedRule = DateGeneration.Rule.Backward;
        this.floatRule = DateGeneration.Rule.Backward;
        this.fixedEndOfMonth = false;
        this.floatEndOfMonth = false;
        this.fixedFirstDate = new JDate();
        this.fixedNextToLastDate = new JDate();
        this.floatFirstDate = new JDate();
        this.floatNextToLastDate = new JDate();
        this.floatSpread = 0.0;
        this.fixedDayCount = new Thirty360();
        this.floatDayCount = index.dayCounter();
        this.engine = new DiscountingSwapEngine(index.termStructure());
    }

    public VanillaSwap value() /* @ReadOnly */ {
        QL.validateExperimentalMode();

        JDate startDate;
        if (!effectiveDate.isNull()) {
            startDate = effectiveDate;
        } else {
            /*@Natural*/ final int fixingDays = iborIndex.fixingDays();
            final JDate referenceDate = new Settings().evaluationDate();
            final JDate spotDate = floatCalendar.advance(referenceDate, fixingDays, TimeUnit.Days);
            startDate = spotDate.add(forwardStart);
        }

        JDate endDate;
        if (terminationDate != null && !terminationDate.isNull()) {
            endDate = terminationDate;
        } else {
            endDate = startDate.add(swapTenor);
        }

        final Schedule fixedSchedule = new Schedule(startDate, endDate,
                fixedTenor, fixedCalendar,
                fixedConvention,
                fixedTerminationDateConvention,
                fixedRule, fixedEndOfMonth,
                fixedFirstDate, fixedNextToLastDate);

        final Schedule floatSchedule = new Schedule(startDate, endDate,
                floatTenor, floatCalendar,
                floatConvention,
                floatTerminationDateConvention,
                floatRule, floatEndOfMonth,
                floatFirstDate, floatNextToLastDate);

        double usedFixedRate = fixedRate;

        if (Double.isNaN(fixedRate)) {
            QL.require(!iborIndex.termStructure().empty(), "no forecasting term structure set to " + iborIndex.name()); // TODO: message

            final VanillaSwap temp = new VanillaSwap(
                    type,
                    nominal,
                    fixedSchedule,
                    0.0,
                    fixedDayCount,
                    floatSchedule,
                    iborIndex,
                    floatSpread,
                    floatDayCount,
                    BusinessDayConvention.Following);

            // ATM on the forecasting curve
            temp.setPricingEngine(new DiscountingSwapEngine(iborIndex.termStructure()));
            usedFixedRate = temp.fairRate();
        }

        final VanillaSwap swap = new VanillaSwap(
                type,
                nominal,
                fixedSchedule,
                usedFixedRate,
                fixedDayCount,
                floatSchedule,
                iborIndex,
                floatSpread,
                floatDayCount,
                BusinessDayConvention.Following);
        swap.setPricingEngine(engine);
        return swap;
    }

    public MakeVanillaSwap receiveFixed(final boolean flag) {
        this.type = flag ? VanillaSwap.Type.Receiver : VanillaSwap.Type.Payer;
        return this;
    }

    public MakeVanillaSwap withType(final VanillaSwap.Type type) {
        this.type = type;
        return this;
    }

    public MakeVanillaSwap withNominal(/* Real */final double n) {
        this.nominal = n;
        return this;
    }

    public MakeVanillaSwap withEffectiveDate(final JDate effectiveDate) {
        this.effectiveDate = effectiveDate;
        return this;
    }

    public MakeVanillaSwap withTerminationDate(final JDate terminationDate) {
        this.terminationDate = terminationDate;
        return this;
    }

    public MakeVanillaSwap withRule(final DateGeneration.Rule r) {
        this.fixedRule = r;
        this.floatRule = r;
        return this;
    }

    public MakeVanillaSwap withDiscountingTermStructure(final Handle<YieldTermStructure> discountingTermStructure) {
        this.engine = (new DiscountingSwapEngine(discountingTermStructure));
        return this;
    }

    public MakeVanillaSwap withFixedLegTenor(final Period t) {
        this.fixedTenor = t;
        return this;
    }

    public MakeVanillaSwap withFixedLegCalendar(final Calendar cal) {
        this.fixedCalendar = cal;
        return this;
    }

    public MakeVanillaSwap withFixedLegConvention(final BusinessDayConvention bdc) {
        this.fixedConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.fixedTerminationDateConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegRule(final DateGeneration.Rule r) {
        this.fixedRule = r;
        return this;
    }

    public MakeVanillaSwap withFixedLegEndOfMonth(final boolean flag) {
        this.fixedEndOfMonth = flag;
        return this;
    }

    public MakeVanillaSwap withFixedLegFirstDate(final JDate d) {
        this.fixedFirstDate = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegNextToLastDate(final JDate d) {
        this.fixedNextToLastDate = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegDayCount(final DayCounter dc) {
        this.fixedDayCount = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTenor(final Period t) {
        this.floatTenor = t;
        return this;
    }

    public MakeVanillaSwap withFloatingLegCalendar(final Calendar cal) {
        this.floatCalendar = cal;
        return this;
    }

    public MakeVanillaSwap withFloatingLegConvention(final BusinessDayConvention bdc) {
        this.floatConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTerminationDateConvention(final BusinessDayConvention bdc) {
        this.floatTerminationDateConvention = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegRule(final DateGeneration.Rule r) {
        this.floatRule = r;
        return this;
    }

    public MakeVanillaSwap withFloatingLegEndOfMonth(final boolean flag) {
        this.floatEndOfMonth = flag;
        return this;
    }

    public MakeVanillaSwap withFloatingLegFirstDate(final JDate d) {
        this.floatFirstDate = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegNextToLastDate(final JDate d) {
        this.floatNextToLastDate = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegDayCount(final DayCounter dc) {
        this.floatDayCount = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegSpread(/* Spread */final double sp) {
        this.floatSpread = sp;
        return this;
    }

}
