package vn.ainalyzing.api.analysis;

/**
 * One supplier quote. sellableDays is null while the supplier has not confirmed the batch's shelf life.
 */
public record Quote(int units, long goodsVnd, long deliveryVnd, Integer sellableDays) {

    /** C in BR-01: goods plus delivery. */
    public long costVnd() {
        return goodsVnd + deliveryVnd;
    }

    public Quote withSellableDays(Integer days) {
        return new Quote(units, goodsVnd, deliveryVnd, days);
    }

    public Quote withGoodsVnd(long goods) {
        return new Quote(units, goods, deliveryVnd, sellableDays);
    }
}
