/*
 Copyright (C) 2011 Tim Blackler

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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

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
package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Africa.ZARCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.JDate;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.SouthAfrica;

/**
 * Johannesburg Interbank Agreed Rate
 *
 * TODO check settlement days and day-count convention.
 */
public class Jibar extends IborIndex {

    public Jibar(final Period tenor) {
        this(tenor, new Handle<YieldTermStructure>(
                new AbstractYieldTermStructure() {
            @Override
            protected double discountImpl(final double t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public JDate maxDate() {
                throw new UnsupportedOperationException();
            }
        }
        ));
    }

    public Jibar(final Period tenor,
            final Handle<YieldTermStructure> h) {
        super("Jibar", tenor, 0,
                new ZARCurrency(),
                new SouthAfrica(),
                BusinessDayConvention.ModifiedFollowing,
                false,
                new Actual365Fixed(),
                h);
    }

}