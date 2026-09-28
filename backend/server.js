import express from "express";
import cors from "cors";
import "dotenv/config";
import pg from "pg";
const {Pool}=pg;
const app=express();
app.use(cors());
app.use(express.json({limit:"2mb"}));
const pool=process.env.DATABASE_URL?new Pool({connectionString:process.env.DATABASE_URL,ssl:process.env.DATABASE_SSL==="false"?false:{rejectUnauthorized:false}}):null;
app.get("/health",(_req,res)=>res.json({ok:true,service:"CardioSonic API"}));
app.post("/api/measurements",async(req,res)=>{
 if(!pool)return res.status(503).json({error:"DATABASE_URL is not configured"});
 const {userId,bpm,confidence,quality,snrDb,durationMs,deviceName,sampleRate}=req.body;
 try{
  const q="INSERT INTO measurements(user_id,bpm,confidence,quality,snr_db,duration_ms,device_name,sample_rate) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id,created_at";
  const r=await pool.query(q,[userId??null,bpm??null,confidence??0,quality??0,snrDb??0,durationMs??0,deviceName??null,sampleRate??16000]);
  res.status(201).json(r.rows[0]);
 }catch(_){res.status(500).json({error:"Database write failed"});}
});
app.get("/api/measurements/:userId",async(req,res)=>{
 if(!pool)return res.status(503).json({error:"DATABASE_URL is not configured"});
 try{const r=await pool.query("SELECT * FROM measurements WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100",[req.params.userId]);res.json(r.rows);}
 catch(_){res.status(500).json({error:"Database read failed"});}
});
const port=Number(process.env.PORT||8080);
app.listen(port,()=>console.log("CardioSonic API listening on :"+port));