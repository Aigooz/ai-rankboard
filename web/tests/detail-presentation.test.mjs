import test from 'node:test';
import assert from 'node:assert/strict';
import {estimateBlendedPrice,rawPriceLabel,mergeModelMetadata,sameVendorModels,detailDate,parameterLabel,selectPriceRecord,scoreUnit} from '../detail-presentation.mjs';
test('blended price rejects incomplete invalid and unknown currency; explicit zeros remain zero',()=>{
 for(const entry of [{priceIn:1},{priceIn:1,priceOut:2},{priceIn:1,priceOut:2,currency:'EUR'},{priceIn:-1,priceOut:2,currency:'CNY'},{priceIn:Infinity,priceOut:2,currency:'USD'},{priceIn:'1',priceOut:2,currency:'CNY'}])assert.equal(estimateBlendedPrice(entry),null);
 assert.equal(estimateBlendedPrice({priceIn:0,priceOut:0,currency:'USD'}),0);
 assert.equal(estimateBlendedPrice({priceIn:1,priceOut:3,currency:'USD'}),10.8);
 assert.match(rawPriceLabel(1,null),/币种未获取/);assert.match(rawPriceLabel(1,'EUR'),/EUR/);assert.equal(rawPriceLabel(null,'CNY'),'未获取');
});
test('metadata merge fills nulls but never combines price components from different records',()=>{
 const merged=mergeModelMetadata({vendor:'Vendor',contextWindow:'32K',priceIn:1,priceOut:2,currency:'USD'},{vendor:null,contextWindow:null,priceOut:4,currency:'CNY'});
 assert.equal(merged.vendor,'Vendor');assert.equal(merged.contextWindow,'32K');assert.equal(merged.priceIn,undefined);assert.equal(merged.priceOut,4);assert.equal(estimateBlendedPrice(merged),null);
});
test('related models exclude self duplicates unknown vendors and unrelated vendors',()=>{
 assert.deepEqual(sameVendorModels({slug:'a',vendor:'Unknown'},[{slug:'b',vendor:'Unknown'}]),[]);
 assert.deepEqual(sameVendorModels({slug:'a',vendor:'Vendor'},[{slug:'a',vendor:'Vendor'},{slug:'b',vendor:'Vendor'},{slug:'b',vendor:'Vendor'},{slug:'c',vendor:'Other'}]).map(x=>x.slug),['b']);
});
test('release params and score unit preserve absent and source-specific metadata',()=>{
 assert.equal(detailDate('2026-02-31'),'未获取');assert.equal(detailDate(null),'未获取');assert.equal(detailDate('2026-10-19'),'2026-10-19');
 assert.equal(parameterLabel(-1),'未获取');assert.equal(parameterLabel(0),'未获取');assert.equal(scoreUnit({score_type:'arena_elo'}),'Elo');assert.equal(scoreUnit({metric:{unit:'%'}}),'%');assert.equal(scoreUnit({}),null);
});

test('representative pricing prefers overall then board slug without joining halves',()=>{
 const a={slug:'x',priceIn:1},b={slug:'x',priceOut:3},overall={slug:'x',priceIn:8,priceOut:9};
 assert.equal(selectPriceRecord({z:[b],a:[a],overall:[overall]},'x').entry,overall);
 assert.equal(selectPriceRecord({overall:[overall],a:[a],z:[b]},'x').entry,overall);
 assert.equal(selectPriceRecord({z:[b],a:[a]},'x').entry,a);
 assert.equal(selectPriceRecord({a:[a],z:[b]},'x').entry,a);
 assert.equal(selectPriceRecord({},'x'),null);
});
